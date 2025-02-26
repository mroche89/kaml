/*

   Copyright 2018-2021 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/

package com.charleskorn.kaml

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
internal class YamlObjectInput(map: YamlMap, context: SerializersModule, configuration: YamlConfiguration) : YamlMapLikeInputBase(map, context, configuration) {
    private val entriesList = map.entries.entries.toList()
    private var nextIndex = 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (true) {
            if (nextIndex == entriesList.size) {
                return CompositeDecoder.DECODE_DONE
            }

            val currentEntry = entriesList[nextIndex]
            currentKey = currentEntry.key
            val fieldDescriptorIndex = descriptor.getElementIndex(propertyName)

            if (fieldDescriptorIndex == CompositeDecoder.UNKNOWN_NAME) {
                if (configuration.strictMode) {
                    throwUnknownProperty(propertyName, currentKey.path, descriptor)
                } else {
                    nextIndex++
                    continue
                }
            }

            try {
                currentValueDecoder = createFor(
                    entriesList[nextIndex].value,
                    serializersModule,
                    configuration,
                    descriptor.getElementDescriptor(fieldDescriptorIndex)
                )
            } catch (e: IncorrectTypeException) {
                throw InvalidPropertyValueException(propertyName, e.message, e.path, e)
            }

            currentlyReadingValue = true
            nextIndex++

            return fieldDescriptorIndex
        }
    }

    private fun throwUnknownProperty(name: String, path: YamlPath, desc: SerialDescriptor): Nothing {
        val knownPropertyNames = (0 until desc.elementsCount)
            .map { desc.getElementName(it) }
            .toSet()

        throw UnknownPropertyException(name, knownPropertyNames, path)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (haveStartedReadingEntries) {
            return fromCurrentValue { beginStructure(descriptor) }
        }

        return super.beginStructure(descriptor)
    }
}
