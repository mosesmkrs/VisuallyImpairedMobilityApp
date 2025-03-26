package com.example.newapp.accessibility
//
//import android.content.Context
//import android.speech.tts.TextToSpeech
//import java.util.Locale
//
//class TTSManager(context: Context) {
//    private var tts: TextToSpeech = TextToSpeech(context) { status ->
//        if (status == TextToSpeech.SUCCESS) {
//            tts.language = Locale.US
//        }
//    }
//
//    fun speak(text: String) {
//        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
//    }
//
//    fun shutdown() {
//        tts.stop()
//        tts.shutdown()
//    }
//}
