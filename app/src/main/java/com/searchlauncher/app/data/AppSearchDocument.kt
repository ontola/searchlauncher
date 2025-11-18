package com.searchlauncher.app.data

import androidx.appsearch.annotation.Document
import androidx.appsearch.app.AppSearchSchema

@Document
data class AppSearchDocument(
        @Document.Namespace val namespace: String = "apps",
        @Document.Id val id: String, // Package Name
        @Document.Score val score: Int,
        @Document.StringProperty(
                indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES
        )
        val name: String // App Name
)
