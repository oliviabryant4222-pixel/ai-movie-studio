// RealFrame experimental on-device text-to-video bridge.
// Uses the same pinned MIT stable-diffusion.cpp CPU-native inference library
// already shipped for local image generation. No separate app or HTTP server.
#include <jni.h>
#include <stdint.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include "stable-diffusion.h"

extern std::atomic<bool> g_realframe_model_busy;
namespace {
std::mutex s_mutex;
sd_ctx_t* s_active=nullptr;

std::atomic<bool> s_cancel{false};
void fail(JNIEnv* env,const std::string& message){
    jclass klass=env->FindClass("java/lang/IllegalStateException");
    if(klass)env->ThrowNew(klass,message.c_str());
}
std::string fromJava(JNIEnv* env,jstring java){
    if(!java)return "";
    const char* ptr=env->GetStringUTFChars(java,nullptr);
    if(!ptr)return "";
    std::string text(ptr);
    env->ReleaseStringUTFChars(java,ptr);
    return text;
}
struct Exclusive {
    bool ok;
    Exclusive() {
        bool expected=false;
        ok=g_realframe_model_busy.compare_exchange_strong(expected,true);
        if(ok)s_cancel.store(false);
    }
    ~Exclusive(){if(ok)g_realframe_model_busy.store(false);}
};
struct Context {
    sd_ctx_t* ptr;
    explicit Context(sd_ctx_t* instance):ptr(instance){}
    ~Context(){
        if(ptr){
            {
                std::lock_guard<std::mutex> lock(s_mutex);
                if(s_active==ptr)s_active=nullptr;
            }
            free_sd_ctx(ptr);
        }
    }
};
}
extern "C" JNIEXPORT void JNICALL
Java_studio_realframe_LocalVideoEngine_nativeCancel(JNIEnv*,jclass){
    s_cancel.store(true);
    std::lock_guard<std::mutex> lock(s_mutex);
    if(s_active)sd_cancel_generation(s_active,SD_CANCEL_ALL);
}

/** Real generate_video() for small Wan2.1 T2V models.
 * Accepts 3 real model component files (diffusion, T5, Wan VAE).
 * Calls Java FrameSink.onFrame(index,width,height,fps,int[] ARGB) one by one.
 * Java encodes H.264 MP4 locally with Android MediaCodec.
 * No video model is bundled and Snapdragon 8 Gen3/12GB feasibility is unknown.
 */
extern "C" JNIEXPORT jint JNICALL
Java_studio_realframe_LocalVideoEngine_nativeGenerate(
    JNIEnv* env,jclass,jstring jdiffusion,jstring jt5,jstring jvae,
    jstring jprompt,jstring jnegative,
    jint width,jint height,jint videoFrames,jint sampleSteps,
    jlong seed,jint threadCount,jobject listener) {
    if(!listener || width<192 || width>512 || width%64!=0 ||
       height<192 || height>512 || height%64!=0 ||
       videoFrames<5 || videoFrames>33 || (videoFrames-1)%4!=0 ||
       sampleSteps<1 || sampleSteps>30 || threadCount<1 || threadCount>8 ||
       seed<1 || seed>2147483647LL) {
        fail(env,"Invalid local video settings. Use 192-512 multiples of 64, frames 5/9/13/17/21/25/29/33.");
        return 0;
    }
    std::string diffusion=fromJava(env,jdiffusion);
    std::string t5=fromJava(env,jt5);
    std::string vae=fromJava(env,jvae);
    std::string prompt=fromJava(env,jprompt);
    std::string negative=fromJava(env,jnegative);
    if(diffusion.empty() || t5.empty() || vae.empty() || prompt.empty()) {
        fail(env,"Diffusion model, T5 text encoder, VAE and prompt are all required");return 0;
    }
    Exclusive job;
    if(!job.ok) {fail(env,"Another local video inference job is running");return 0;}
    jclass sinkClass=env->GetObjectClass(listener);
    if(!sinkClass)return 0;
    jmethodID send=env->GetMethodID(sinkClass,"onFrame","(IIII[I)V");
    env->DeleteLocalRef(sinkClass);
    if(!send)return 0;

    sd_ctx_params_t context{};
    sd_ctx_params_init(&context);
    context.diffusion_model_path=diffusion.c_str();
    context.t5xxl_path=t5.c_str();
    context.vae_path=vae.c_str();
    context.n_threads=static_cast<int>(threadCount);
    context.enable_mmap=true;
    // GGML CPU only. No unsupported Qualcomm QNN/NPU claim.
    sd_ctx_t* pointer=new_sd_ctx(&context);
    if(!pointer){
        fail(env,"Could not load video model. Need compatible Wan T2V diffusion, T5 and VAE plus enough RAM.");
        return 0;
    }
    Context holder(pointer);
    {
        std::lock_guard<std::mutex> guard(s_mutex);
        s_active=pointer;
        if(s_cancel.load())sd_cancel_generation(pointer,SD_CANCEL_ALL);
    }
    if(s_cancel.load())return 0;
    sd_vid_gen_params_t params{};
    sd_vid_gen_params_init(&params);
    params.prompt=prompt.c_str();
    params.negative_prompt=negative.c_str();
    params.width=width;
    params.height=height;
    params.video_frames=videoFrames;
    params.fps=16;
    params.seed=seed;
    params.sample_params.sample_steps=sampleSteps;
    params.sample_params.sample_method=EULER_SAMPLE_METHOD;
    params.sample_params.guidance.txt_cfg=6.0f;
    params.sample_params.flow_shift=3.0f;

    sd_video_generation_result_t* output=nullptr;
    bool generated=generate_video(pointer,&params,&output);
    if(!generated || !output || !output->frames || output->frame_count<1) {
        if(output)free_sd_video_generation_result(output);
        if(s_cancel.load())return 0;
        fail(env,"Local diffusion video failed: model mismatch, unsupported layers, thermal limit or out-of-memory.");
        return 0;
    }
    const int fps=output->fps>0&&output->fps<=60?output->fps:16;
    const int frameCount=output->frame_count;
    int sent=0;
    for(int i=0;i<frameCount && !s_cancel.load();i++){
        const sd_image_t& frame=output->frames[i];
        if(frame.width!=static_cast<uint32_t>(width) ||
           frame.height!=static_cast<uint32_t>(height) ||
           (frame.channel!=3 && frame.channel!=4) || !frame.data){
            free_sd_video_generation_result(output);
            fail(env,"Native video returned unexpected frame format");
            return sent;
        }
        const size_t pixelCount=static_cast<size_t>(frame.width)*frame.height;
        std::vector<jint> packed(pixelCount);
        for(size_t p=0;p<pixelCount;p++){
            const uint8_t* color=frame.data+frame.channel*p;
            packed[p]=static_cast<jint>(0xFF000000u|
                (uint32_t(color[0])<<16)|
                (uint32_t(color[1])<<8)|
                uint32_t(color[2]));
        }
        jintArray javaPixels=env->NewIntArray(static_cast<jsize>(pixelCount));
        if(!javaPixels)break;
        env->SetIntArrayRegion(javaPixels,0,static_cast<jsize>(pixelCount),packed.data());
        if(!env->ExceptionCheck())
            env->CallVoidMethod(listener,send,static_cast<jint>(i),
                width,height,static_cast<jint>(fps),javaPixels);
        env->DeleteLocalRef(javaPixels);
        if(env->ExceptionCheck())break;
        sent++;
    }
    free_sd_video_generation_result(output);
    return sent;
}
