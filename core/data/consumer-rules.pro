# Gson reads annotated DTO fields and their generic types reflectively.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keep,allowoptimization,allowobfuscation class com.lashla.chillitest.core.data.dto.**Dto {
    @com.google.gson.annotations.SerializedName <fields>;
}
