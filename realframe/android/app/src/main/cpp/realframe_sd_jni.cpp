#include <jni.h>
#include <stdint.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include "stable-diffusion.h"
#include <android/log.h>

namespace {
std::mutex g_mutex;
sd_ctx_t *g_active = nullptr;
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
