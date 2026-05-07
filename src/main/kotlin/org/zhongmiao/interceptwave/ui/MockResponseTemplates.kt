package org.zhongmiao.interceptwave.ui

object MockResponseTemplates {
    data class Template(
        val id: String,
        val messageKey: String,
        val payload: String
    )

    val templates: List<Template> = listOf(
        Template(
            id = "success",
            messageKey = "mockapi.template.success",
            payload = """{"code":0,"message":"success","data":{}}"""
        ),
        Template(
            id = "pagination",
            messageKey = "mockapi.template.pagination",
            payload = """{"code":0,"message":"success","data":{"list":[],"total":0,"pageNum":1,"pageSize":10}}"""
        ),
        Template(
            id = "empty",
            messageKey = "mockapi.template.empty",
            payload = """{"code":0,"message":"success","data":null}"""
        ),
        Template(
            id = "error",
            messageKey = "mockapi.template.error",
            payload = """{"code":500,"message":"Internal Server Error","data":null}"""
        ),
        Template(
            id = "auth-expired",
            messageKey = "mockapi.template.auth.expired",
            payload = """{"code":401,"message":"Unauthorized","data":null}"""
        )
    )

    fun byId(id: String): Template? = templates.firstOrNull { it.id == id }
}
