#include <jni.h>
#include <stdint.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include <atomic>
#include "stable-diffusion.h"
#include <android/log.h>

// Shared resource gate across local image and video renderers.
std::atomic<bool> g_realframe_model_busy{false};
namespace {
std::mutex g_mutex;
sd_ctx_t *g_active = nullptr;
// Reject concurrent model loading too, before a context is registered as active.

std::atomic<bool> g_cancel_requested{false};
struct Slot {
  bool owned = false;
  Slot() {
    bool expected = false;
    owned = g_realframe_model_busy.compare_exchange_strong(expected, true);
    if(owned)g_cancel_requested.store(false);
  }
  ~Slot(){if(owned)g_realframe_model_busy.store(false);}
};
void javaError(JNIEnv* env, const std::string& message) {
  jclass klass = env->FindClass("java/lang/IllegalStateException");
  if (klass) env->ThrowNew(klass, message.c_str());
}
std::string asText(JNIEnv* env, jstring text) {
  if (!text) return "";
  const char *raw = env->GetStringUTFChars(text, nullptr);
  if (!raw) return "";
  std::string value(raw);
  env->ReleaseStringUTFChars(text, raw);
  return value;
}
struct Scope {
  sd_ctx_t* ptr=nullptr;
  explicit Scope(sd_ctx_t* p):ptr(p) {}
  ~Scope() {
    if (ptr) {
      { std::lock_guard<std::mutex> lock(g_mutex); if (g_active == ptr) g_active=nullptr; }
      free_sd_ctx(ptr);
    }
  }
};
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_studio_realframe_LocalImageEngine_nativeVersion(JNIEnv* env, jclass) {
  const char* info = sd_get_system_info();
  return env->NewStringUTF(info ? info : "stable-diffusion.cpp / Android CPU");
}

extern "C" JNIEXPORT void JNICALL
Java_studio_realframe_LocalImageEngine_nativeCancel(JNIEnv* env, jclass) {
  g_cancel_requested.store(true);
  std::lock_guard<std::mutex> lock(g_mutex);
  if(g_active) sd_cancel_generation(g_active, SD_CANCEL_ALL);
}

/**
 * Returns packed ARGB pixels from a REAL stable-diffusion.cpp image inference
 * on the phone, not a mock or a remote request. Requires compatible GGUF /
 * safetensors model(s) and enough on-device RAM/disk.
 *
 * Access from a single background worker. The library's model loading and
 * inference can use several GB of RAM and is not performance benchmarked.
 */
extern "C" JNIEXPORT jintArray JNICALL
Java_studio_realframe_LocalImageEngine_nativeGenerate(
    JNIEnv* env, jclass,
    jstring j_model, jstring j_prompt, jstring j_negative,
    jint width, jint height, jint steps, jlong seed, jint threads) {
  if (width < 64 || height < 64 || width > 1024 || height > 1024
      || (width % 64) || (height % 64) || steps < 1 || steps > 100 || threads < 1 || threads > 8) {
    javaError(env,"Invalid size / steps / threads"); return nullptr;
  }
  auto model=asText(env,j_model), prompt=asText(env,j_prompt), negative=asText(env,j_negative);
  if (model.empty() || prompt.empty()) { javaError(env,"Model and prompt required"); return nullptr; }
  Slot slot;
  if(!slot.owned) {javaError(env,"A local AI model is already loading or generating.");return nullptr;}
  sd_ctx_params_t config{};
  sd_ctx_params_init(&config);
  config.model_path=model.c_str();
  config.n_threads=static_cast<int>(threads);
  config.enable_mmap=true;
  // No unsupported QNN/NPU claims. Default GGML CPU backend is used.
  sd_ctx_t *ptr=new_sd_ctx(&config);
  if(!ptr) {
    javaError(env,"Local diffusion model failed to load. Check checkpoint format, model components and available RAM.");return nullptr;
  }
  Scope scope(ptr);
  {
    std::lock_guard<std::mutex> lock(g_mutex);
    if(g_active){javaError(env,"Only one local model job may run at a time.");return nullptr;}
    g_active=ptr;
  }
  if(!sd_ctx_supports_image_generation(ptr)) {
    javaError(env,"This model cannot generate still images.");return nullptr;
  }
  sd_img_gen_params_t params{};
  sd_img_gen_params_init(&params);
  params.prompt=prompt.c_str();
  params.negative_prompt=negative.c_str();
  params.width=width;
  params.height=height;
  params.batch_count=1;
  params.seed=seed;
  params.sample_params.sample_steps=steps;
  // Default sampler, guidance and scheduler are provided by the engine.
  sd_image_generation_result_t* result=nullptr;
  bool ok=generate_image(ptr,&params,&result);
  if(!ok || !result || result->count < 1 || !result->items ||
      !result->items[0].image.data) {
    if(result)free_sd_image_generation_result(result);
    javaError(env,"Local image generation failed. Try a supported quantized SD1.5 model and 256x256.");
    return nullptr;
  }
  const sd_image_t &im=result->items[0].image;
  if(im.width>1024 || im.height>1024 || im.width==0 || im.height==0 || im.channel<3) {
    free_sd_image_generation_result(result);
    javaError(env,"Unexpected image dimensions or channel count");return nullptr;
  }
  size_t count=static_cast<size_t>(im.width)*im.height;
  std::vector<jint> pixels(count);
  for(size_t k=0;k<count;k++) {
    uint8_t r=im.data[k*im.channel];
    uint8_t g=im.data[k*im.channel+1];
    uint8_t b=im.data[k*im.channel+2];
    pixels[k]=static_cast<jint>(0xFF000000u|(uint32_t(r)<<16)|(uint32_t(g)<<8)|uint32_t(b));
  }
  free_sd_image_generation_result(result);
  jintArray arr=env->NewIntArray(static_cast<jsize>(count));
  if(!arr) return nullptr;
  env->SetIntArrayRegion(arr,0,static_cast<jsize>(count),pixels.data());
  return arr;
}


/** Sequential batch inference that loads a compatible model once. 1-8 images.
 * A Java callback receives and saves each image before the next step.
 * Does NOT run simultaneous GPU workers, and requires adequate phone RAM.
 */
extern "C" JNIEXPORT jint JNICALL
Java_studio_realframe_LocalImageEngine_nativeGenerateBatch(
    JNIEnv* env, jclass,
    jstring j_model, jstring j_prompt, jstring j_negative,
    jint width, jint height, jint steps, jlong seed, jint threads,
    jint batchCount, jobject sink) {
  if(width < 64 || height < 64 || width > 1024 || height > 1024 ||
     (width % 64) || (height % 64) || steps < 1 || steps > 100 ||
     threads < 1 || threads > 8 || batchCount < 1 || batchCount > 8 ||
     seed < 1 || seed > 2147483647LL || !sink) {
    javaError(env,"Invalid batch input or callback");return 0;
  }
  auto model=asText(env,j_model), prompt=asText(env,j_prompt), negative=asText(env,j_negative);
  if(model.empty() || prompt.empty()) {javaError(env,"Model and prompt required");return 0;}
  Slot slot;
  if(!slot.owned) {javaError(env,"Another local AI model is loading or generating");return 0;}
  jclass sinkClass=env->GetObjectClass(sink);
  if(!sinkClass)return 0;
  jmethodID onFrame=env->GetMethodID(sinkClass,"onFrame","(I[I)V");
  env->DeleteLocalRef(sinkClass);
  if(!onFrame) {if(!env->ExceptionCheck())javaError(env,"Missing onFrame listener");return 0;}

  sd_ctx_params_t config{};
  sd_ctx_params_init(&config);
  config.model_path=model.c_str();
  config.n_threads=static_cast<int>(threads);
  config.enable_mmap=true;
  sd_ctx_t* ptr=new_sd_ctx(&config);
  if(!ptr){
    javaError(env,"Could not load model: check checkpoint type, model components and RAM");
    return 0;
  }
  Scope scope(ptr);
  {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_active=ptr;
    if(g_cancel_requested.load())sd_cancel_generation(ptr,SD_CANCEL_ALL);
  }
  if(!sd_ctx_supports_image_generation(ptr)){
    javaError(env,"Selected checkpoint cannot generate images");return 0;
  }
  int finished=0;
  for(int i=0;i<batchCount;i++){
    if(g_cancel_requested.load())break;
    sd_img_gen_params_t params{};
    sd_img_gen_params_init(&params);
    params.prompt=prompt.c_str();
    params.negative_prompt=negative.c_str();
    params.width=width; params.height=height; params.batch_count=1;
    // Consistent, reproducible per-image seed series without signed overflow.
    uint64_t seed0=static_cast<uint64_t>(seed - 1);
    params.seed=1+static_cast<int64_t>((seed0+static_cast<uint64_t>(i)*104729ULL)%2147483647ULL);
    params.sample_params.sample_steps=steps;
    sd_image_generation_result_t* result=nullptr;
    bool success=generate_image(ptr,&params,&result);
    if(!success || !result || result->count != 1 || !result->items ||
       !result->items[0].image.data){
      if(result)free_sd_image_generation_result(result);
      if(g_cancel_requested.load())return finished;
      javaError(env,"Inference failed during image "+std::to_string(i+1));return finished;
    }
    const sd_image_t &im=result->items[0].image;
    if(im.width != static_cast<uint32_t>(width) ||
       im.height != static_cast<uint32_t>(height) ||
       (im.channel != 3 && im.channel != 4)) {
      free_sd_image_generation_result(result);
      javaError(env,"Unexpected generated image size/channel count");return finished;
    }
    size_t count=static_cast<size_t>(im.width)*static_cast<size_t>(im.height);
    std::vector<jint> pixels(count);
    for(size_t k=0;k<count;k++){
      const uint8_t* pixel=im.data+k*im.channel;
      pixels[k]=static_cast<jint>(0xFF000000u |
           (uint32_t(pixel[0])<<16) |
           (uint32_t(pixel[1])<<8) |
            uint32_t(pixel[2]));
    }
    free_sd_image_generation_result(result);
    jintArray javaPixels=env->NewIntArray(static_cast<jsize>(count));
    if(!javaPixels)return finished;
    env->SetIntArrayRegion(javaPixels,0,static_cast<jsize>(count),pixels.data());
    if(env->ExceptionCheck()){env->DeleteLocalRef(javaPixels);return finished;}
    env->CallVoidMethod(sink,onFrame,static_cast<jint>(i),javaPixels);
    env->DeleteLocalRef(javaPixels);
    if(env->ExceptionCheck())return finished;
    ++finished;
  }
  return finished;
}


/**
 * img2img: use a reference photo as *actual* diffusion input rather than
 * adding its description to text. 1-4 sequential outputs per model load.
 * This encourages consistency but is NOT face-ID locking / PhotoMaker.
 *
 * Source reference ARGB pixels are copied from Java into an RGB buffer.
 * CPU inference stays on the phone. Caller must hold a single worker thread.
 */
extern "C" JNIEXPORT jint JNICALL
Java_studio_realframe_LocalImageEngine_nativeReferenceBatch(
    JNIEnv* env, jclass,
    jstring j_model,jstring j_prompt,jstring j_negative,
    jint width,jint height,jint steps,jlong seed,jint threads,
    jint batchCount,jintArray referenceARGB,jfloat strength,jobject sink) {
  if(width<64 || width>1024 || height<64 || height>1024 ||
      width%64 || height%64 || steps<1 || steps>100 ||
      seed<1 || seed>2147483647LL || threads<1 || threads>8 ||
      batchCount<1 || batchCount>8 || strength<0.1f || strength>0.95f ||
      !referenceARGB || !sink) {
    javaError(env,"Invalid reference image settings: strength 0.10-0.95");
    return 0;
  }
  const size_t count=static_cast<size_t>(width)*height;
  if(env->GetArrayLength(referenceARGB)!=static_cast<jsize>(count)){
    javaError(env,"Reference photo size must match image generation resolution");
    return 0;
  }
  auto model=asText(env,j_model), prompt=asText(env,j_prompt),
       negative=asText(env,j_negative);
  if(model.empty()||prompt.empty()){
    javaError(env,"Model and prompt required");
    return 0;
  }
  Slot slot;
  if(!slot.owned){
    javaError(env,"Another local AI job is using the model");
    return 0;
  }
  jclass klass=env->GetObjectClass(sink);
  if(!klass)return 0;
  jmethodID onFrame=env->GetMethodID(klass,"onFrame","(I[I)V");
  env->DeleteLocalRef(klass);
  if(!onFrame)return 0;
  std::vector<jint> original(count);
  env->GetIntArrayRegion(referenceARGB,0,static_cast<jsize>(count),original.data());
  if(env->ExceptionCheck())return 0;
  std::vector<uint8_t> rgb(count*3);
  for(size_t i=0;i<count;i++){
    uint32_t color=static_cast<uint32_t>(original[i]);
    rgb[3*i]=static_cast<uint8_t>((color>>16)&255);
    rgb[3*i+1]=static_cast<uint8_t>((color>>8)&255);
    rgb[3*i+2]=static_cast<uint8_t>(color&255);
  }
  original.clear();
  sd_ctx_params_t config{};
  sd_ctx_params_init(&config);
  config.model_path=model.c_str();
  config.n_threads=static_cast<int>(threads);
  config.enable_mmap=true;
  sd_ctx_t* ptr=new_sd_ctx(&config);
  if(!ptr) {
    javaError(env,"Reference AI model could not load. Check format and free RAM.");
    return 0;
  }
  Scope scope(ptr);
  {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_active=ptr;
    if(g_cancel_requested.load())sd_cancel_generation(ptr,SD_CANCEL_ALL);
  }
  if(!sd_ctx_supports_image_generation(ptr)){
    javaError(env,"This checkpoint cannot generate SD still images");
    return 0;
  }
  sd_image_t reference{};
  reference.width=static_cast<uint32_t>(width);
  reference.height=static_cast<uint32_t>(height);
  reference.channel=3;
  reference.data=rgb.data();
  int completed=0;
  for(int imageIndex=0;imageIndex<batchCount;imageIndex++){
    if(g_cancel_requested.load())break;
    sd_img_gen_params_t params{};
    sd_img_gen_params_init(&params);
    params.prompt=prompt.c_str();
    params.negative_prompt=negative.c_str();
    params.init_image=reference;
    params.strength=strength; // 0.25 preserves more; 0.7 changes more.
    params.width=width;params.height=height;
    params.seed=1+static_cast<int64_t>(
      (static_cast<uint64_t>(seed-1)+
       static_cast<uint64_t>(imageIndex)*104729ULL)%2147483647ULL);
    params.batch_count=1;
    params.sample_params.sample_steps=steps;
    sd_image_generation_result_t* result=nullptr;
    bool ok=generate_image(ptr,&params,&result);
    if(!ok||!result||result->count!=1||!result->items||
       !result->items[0].image.data){
      if(result)free_sd_image_generation_result(result);
      if(g_cancel_requested.load())return completed;
      javaError(env,"Reference diffusion failed. Reduce resolution/steps, or try a compatible SD1.5 model.");
      return completed;
    }
    const sd_image_t &im=result->items[0].image;
    if(im.width!=static_cast<uint32_t>(width)||
       im.height!=static_cast<uint32_t>(height)||
       (im.channel!=3&&im.channel!=4)){
      free_sd_image_generation_result(result);
      javaError(env,"Reference diffusion returned unexpected output format");
      return completed;
    }
    std::vector<jint> pixels(count);
    for(size_t i=0;i<count;i++){
      const uint8_t* p=im.data+static_cast<size_t>(im.channel)*i;
      pixels[i]=static_cast<jint>(0xFF000000u|
       (uint32_t(p[0])<<16)|(uint32_t(p[1])<<8)|uint32_t(p[2]));
    }
    free_sd_image_generation_result(result);
    jintArray returned=env->NewIntArray(static_cast<jsize>(count));
    if(!returned)return completed;
    env->SetIntArrayRegion(returned,0,static_cast<jsize>(count),pixels.data());
    if(!env->ExceptionCheck())env->CallVoidMethod(sink,onFrame,static_cast<jint>(imageIndex),returned);
    env->DeleteLocalRef(returned);
    if(env->ExceptionCheck())return completed;
    ++completed;
  }
  return completed;
}
