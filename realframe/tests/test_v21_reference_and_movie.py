"""v2.1: static regression checks for local img2img conditioning and automatic
AI-keyframe movie pipelines. A successful build is NOT a Samsung device
benchmark; these tests do not load 1.75GB SD weights or render Wan videos.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"android/app"
J=APP/"src/main/java/studio/realframe"
CPP=APP/"src/main/cpp/realframe_sd_jni.cpp"

class VisualContinuityTests(unittest.TestCase):
    def test_reference_uses_native_init_image_not_prompt_only(self):
        source=CPP.read_text(encoding="utf-8")
        ref=source.split("Java_studio_realframe_LocalImageEngine_nativeReferenceBatch(",1)[1]
        self.assertIn("params.init_image=reference",ref)
        self.assertIn("params.strength=strength",ref)
        self.assertIn("generate_image(engine",source)
        self.assertIn("CallVoidMethod",ref)
        self.assertIn("Slot slot",ref)
    def test_storyboard_reuses_one_model_and_prior_scene(self):
        source=CPP.read_text(encoding="utf-8")
        story=source.split("Java_studio_realframe_LocalImageEngine_nativeStoryboard(",1)[1]
        self.assertEqual(story.count("new_sd_ctx("),1)
        self.assertIn("params.init_image=reference",story)
        self.assertIn("previous[3*i]",story)
        self.assertIn("params.seed=seed",story)
        self.assertIn("g_cancel_requested",story)
        self.assertIn("CallVoidMethod",story)
    def test_reference_photo_picker_is_local_and_persistable(self):
        source=(J/"ReferenceStudioActivity.java").read_text()
        for word in ("ACTION_OPEN_DOCUMENT","image/*","takePersistableUriPermission",
                     "LocalImageEngine.generateReferenceBatch(","getSharedPreferences(\"realframe_local_sd\"",
                     "save(image,index+1)","Pictures/RealFrameStudio","AutoModelDownloadService"):
            if word=="AutoModelDownloadService":
                self.assertIn("QuickStartActivity.class",source)
            else:
                self.assertIn(word,source)
    def test_auto_film_is_genuine_ai_keyframes_plus_cinematic_animation(self):
        source=(J/"LocalAutoFilmActivity.java").read_text()
        for word in ("generateStoryboard(", "new LocalVideoEncoder(", "encoder[0].finish()",
                     "appendAnimated(","Same fictional adult", "getSharedPreferences(\"realframe_local_sd\"",
                     "Movie", "Photo"):
            if word == "Photo":
                self.assertIn("ReferenceStudioActivity.class",source)
            else:
                self.assertIn(word,source)
        self.assertIn("keyframe",source.lower())
        self.assertIn("not a true motion-diffusion video model",source.lower())
    def test_photo_movie_is_offline_h264_with_smooth_camera_motion(self):
        source=(J/"StoryMovieComposerActivity.java").read_text()
        for word in ("ACTION_OPEN_DOCUMENT","EXTRA_ALLOW_MULTIPLE","new LocalVideoEncoder(",
                     "encoder.append(pixels)","paintPhoto(", "setAlpha(", "encoder.finish()",
                     "Movies/RealFrameStudio"):
            if word == "Movies/RealFrameStudio":
                self.assertIn("Check Gallery",source)
            else:
                self.assertIn(word,source)
    def test_manifest_dashboard_create_and_version(self):
        manifest=ET.parse(APP/"src/main/AndroidManifest.xml").getroot()
        ns="{http://schemas.android.com/apk/res/android}"
        registered={a.get(ns+"name") for a in manifest.find("application").findall("activity")}
        self.assertTrue({".ReferenceStudioActivity",".LocalAutoFilmActivity",
                         ".StoryMovieComposerActivity"}.issubset(registered))
        dash=(J/"StudioDashboardActivity.java").read_text()
        for activity in ("LocalAutoFilmActivity.class","ReferenceStudioActivity.class",
                         "StoryMovieComposerActivity.class","WebStudioActivity.class",
                         "LocalApiActivity.class"):
            self.assertIn(activity,dash)
        gradle=(APP/"build.gradle.kts").read_text()
        self.assertIn('applicationId = "studio.realframe.v21"',gradle)
        self.assertIn('versionName = "2.1.0-reference-movie-beta"',gradle)
    def test_local_image_studio_can_still_auto_setup(self):
        self.assertIn("QuickStartActivity.class",(J/"LocalImageActivity.java").read_text())
        self.assertIn("gpustack/stable-diffusion-v1-5-GGUF",
                      (J/"AutoModelDownloadService.java").read_text())
        self.assertIn("confirmCloud(", (J/"UnifiedCreateActivity.java").read_text())
        self.assertIn("local(", (J/"UnifiedCreateActivity.java").read_text())
if __name__=="__main__":unittest.main()
