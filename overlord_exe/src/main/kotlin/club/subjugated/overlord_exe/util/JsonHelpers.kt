package club.subjugated.overlord_exe.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

fun explainFields(clazz: KClass<*>): Map<String, String> {
    return clazz.memberProperties.associate { prop ->
        val name = prop.name
        val type = prop.returnType.toString()
        name to type
    }
}

fun <T : Any> decodeJsonToType(json: String, clazz: KClass<T>): T {
    val serializer = kotlinx.serialization.serializer(clazz, typeArgumentsSerializers = emptyList(), isNullable = true) as KSerializer<T>
    return Json.decodeFromString(serializer, json)
}