# R8 混淆保留规则（release 构建开启 minifyEnabled）
# 基线：Room 反射实例化 *_Impl、KSP 生成代码、Compose 运行时。
# 依赖库自带 consumer rules（Room/OkHttp/Compose 官方已覆盖），此处为显式兜底，避免误删。

# ===== Room + KSP =====
# Room 通过反射按 "<数据库类名>_Impl" 实例化实现（Room.getGeneratedImplementation）
-keep class **.*_Impl { *; }

# 数据库/DAO/实体/TypeConverter：保留注解类与构造器，防混淆后反射失败
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *
-keep @androidx.room.TypeConverter class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# KSP 生成的 SQL 语句/实现不参与收缩（防 NoSuchMethodError）
-keepclassmembers class * {
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
    @androidx.room.Query <methods>;
}

# ===== Compose =====
# Compose 编译器插件生成代码依赖运行时符号；官方 consumer rules 已覆盖，此处兜底保留关键运行时类
-keep,allowobfuscation,allowshrinking class androidx.compose.runtime.ComposerKt { *; }

# ===== 通用 =====
# 保留来源信息便于崩溃定位（可选）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
