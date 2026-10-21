# ==============================================================================
# ProGuard / R8 Rules — Saldo Livre (Android Release Build)
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Atributos Base Essenciais para Reflexão, Generics e Coroutines
# ------------------------------------------------------------------------------
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable

# ------------------------------------------------------------------------------
# 2. Data Models do Domínio (Saldo Livre)
# Preserva todas as data classes, enums e propriedades usadas pelo Gson (Mock,
# ChatSessionManager, LearnedRulesManager) e pelo Cloud Firestore.
# ------------------------------------------------------------------------------
-keep class com.saldolivre.app.data.model.** { *; }
-keepnames class com.saldolivre.app.data.model.** { *; }

# Modelos auxiliares de gráficos embutidos em ChatMessage (serializados via Gson)
-keep class com.saldolivre.app.ui.screens.chat.ChartPayload { *; }
-keep class com.saldolivre.app.ui.screens.chat.ChartDataItem { *; }
-keep class com.saldolivre.app.ui.screens.chat.ChartType { *; }

# Modelos de parsing de extratos e IA
-keep class com.saldolivre.app.data.parser.** { *; }
-keep class com.saldolivre.app.data.ai.GeminiExecutionResult { *; }
-keep class com.saldolivre.app.data.ai.CategoryGuess { *; }
-keep class com.saldolivre.app.data.ai.ExtractedIntent { *; }

# Preserva métodos de Enums (values/valueOf/entries) exigidos por Gson e Firestore
-keepclassmembers enum com.saldolivre.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ------------------------------------------------------------------------------
# 3. Google Gson (Serialização / Desserialização JSON e TypeToken)
# ------------------------------------------------------------------------------
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}

# Evita que o R8 remova construtores sem argumentos usados via Unsafe/Reflection
-keepclassmembers class com.saldolivre.app.data.model.** {
    <init>(...);
}

# ------------------------------------------------------------------------------
# 4. Firebase & Cloud Firestore
# ------------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.DocumentId <methods>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
    @com.google.firebase.firestore.ServerTimestamp <methods>;
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
    @com.google.firebase.firestore.IgnoreExtraProperties <fields>;
}

# ------------------------------------------------------------------------------
# 5. Google Generative AI SDK (Gemini) & Kotlinx Serialization
# O SDK oficial com.google.ai.client.generativeai utiliza Kotlinx Serialization
# e Ktor internamente para comunicar com a API REST do Gemini.
# ------------------------------------------------------------------------------
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Preserva os Companions e Serializers gerados pelo compilador Kotlinx Serialization
-keep,includedescriptorclasses class com.google.ai.client.generativeai.**$$serializer { *; }
-keepclassmembers class com.google.ai.client.generativeai.** {
    *** Companion;
}
-keepclasseswithmembers class com.google.ai.client.generativeai.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor Client (usado internamente pelo SDK do Gemini)
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**

