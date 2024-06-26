/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bk.devops.git.core.i18n

import com.tencent.bk.devops.git.core.exception.TranslationBundleException
import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

/**
 * NLS (National Language Support)
 */
open class TranslationBundle {

    companion object {
        private val logger = LoggerFactory.getLogger(TranslationBundle::class.java)
        var cachedBundles: MutableMap<Locale, MutableMap<Class<*>, TranslationBundle>> = ConcurrentHashMap()

        @Suppress("UNCHECKED_CAST")
        fun <T : TranslationBundle> lookupBundle(locale: Locale, type: Class<T>): T {
            try {
                var bundles = cachedBundles[locale]
                if (bundles == null) {
                    bundles = HashMap()
                    cachedBundles[locale] = bundles
                }
                var bundle = bundles[type]
                if (bundle == null) {
                    bundle = type.getDeclaredConstructor().newInstance()
                    bundle.load(locale)
                    bundles[type] = bundle
                }
                return bundle as T
            } catch (ignore: Exception) {
                logger.error("looking of translation bundle failed for [${type.name}, $locale]", ignore)
                throw TranslationBundleException(
                    bundleClass = type,
                    locale = locale,
                    errorMsg = "Loading of translation bundle failed for [${type.name}, $locale]",
                    cause = ignore
                )
            }
        }
    }

    private fun load(locale: Locale) {
        val bundleClass = javaClass
        val resourceBundle = ResourceBundle.getBundle(bundleClass.simpleName, locale, bundleClass.classLoader)
        bundleClass.declaredFields.forEach { field ->
            if (field.type == String::class.java) {
                val translatedText =
                    String(resourceBundle.getString(field.name).toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                field.isAccessible = true
                field.set(this, translatedText)
            }
        }
    }
}
