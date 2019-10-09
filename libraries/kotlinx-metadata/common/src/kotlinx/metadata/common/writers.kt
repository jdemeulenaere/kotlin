/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlinx.metadata.common

import kotlinx.metadata.impl.PackageWriter
import org.jetbrains.kotlin.backend.common.serialization.metadata.KlibMetadataStringTable

class CommonPackageWriter : PackageWriter(KlibMetadataStringTable())