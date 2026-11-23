package com.example.domain.model

enum class FormatCategory(val label: String) {
    DOCUMENT("Documents"),
    SPREADSHEET("Spreadsheets"),
    IMAGE("Images"),
    ARCHIVE("Archives")
}

enum class FileFormat(
    val ext: String,
    val displayName: String,
    val mimeType: String,
    val category: FormatCategory
) {
    PDF("pdf", "PDF", "application/pdf", FormatCategory.DOCUMENT),
    DOCX("docx", "Word", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", FormatCategory.DOCUMENT),
    TXT("txt", "Text", "text/plain", FormatCategory.DOCUMENT),
    MD("md", "Markdown", "text/markdown", FormatCategory.DOCUMENT),
    HTML("html", "HTML", "text/html", FormatCategory.DOCUMENT),

    XLSX("xlsx", "Excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", FormatCategory.SPREADSHEET),
    CSV("csv", "CSV", "text/csv", FormatCategory.SPREADSHEET),
    TSV("tsv", "TSV", "text/tab-separated-values", FormatCategory.SPREADSHEET),
    JSON("json", "JSON", "application/json", FormatCategory.SPREADSHEET),
    XML("xml", "XML", "application/xml", FormatCategory.SPREADSHEET),

    PNG("png", "PNG", "image/png", FormatCategory.IMAGE),
    JPG("jpg", "JPG", "image/jpeg", FormatCategory.IMAGE),
    WEBP("webp", "WEBP", "image/webp", FormatCategory.IMAGE),
    BMP("bmp", "BMP", "image/bmp", FormatCategory.IMAGE),

    ZIP("zip", "ZIP", "application/zip", FormatCategory.ARCHIVE);

    companion object {
        fun fromExtension(ext: String): FileFormat? {
            val clean = ext.trim().lowercase().removePrefix(".")
            return when (clean) {
                "jpeg" -> JPG
                "htm" -> HTML
                "markdown" -> MD
                else -> entries.firstOrNull { it.ext == clean }
            }
        }

        fun fromMimeType(mime: String?): FileFormat? {
            val clean = mime?.lowercase()?.substringBefore(';')?.trim() ?: return null
            return when (clean) {
                "application/pdf" -> PDF
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX
                "text/plain" -> TXT
                "text/markdown", "text/x-markdown" -> MD
                "text/html" -> HTML
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> XLSX
                "text/csv", "text/comma-separated-values", "application/csv" -> CSV
                "text/tab-separated-values" -> TSV
                "application/json" -> JSON
                "application/xml", "text/xml" -> XML
                "image/png" -> PNG
                "image/jpeg", "image/jpg" -> JPG
                "image/webp" -> WEBP
                "image/bmp", "image/x-ms-bmp" -> BMP
                "application/zip", "application/x-zip-compressed" -> ZIP
                else -> null
            }
        }

        fun mimeFilterFor(formats: Set<FileFormat>): Array<String> {
            val mimes = linkedSetOf<String>()
            formats.forEach { fmt ->
                when (fmt) {
                    JPG -> {
                        mimes.add("image/jpeg")
                        mimes.add("image/jpg")
                    }
                    CSV -> {
                        mimes.add("text/csv")
                        mimes.add("text/comma-separated-values")
                        mimes.add("application/csv")
                    }
                    MD -> {
                        mimes.add("text/markdown")
                        mimes.add("text/x-markdown")
                        mimes.add("text/plain")
                    }
                    XML -> {
                        mimes.add("application/xml")
                        mimes.add("text/xml")
                    }
                    ZIP -> {
                        mimes.add("application/zip")
                        mimes.add("application/x-zip-compressed")
                    }
                    JSON -> {
                        mimes.add("application/json")
                        mimes.add("text/plain")
                    }
                    else -> mimes.add(fmt.mimeType)
                }
            }
            return mimes.toTypedArray()
        }

        fun supportedTargetsFor(source: FileFormat, multipleFiles: Boolean = false): List<FileFormat> {
            if (multipleFiles) {
                return when (source.category) {
                    FormatCategory.IMAGE -> listOf(PDF, JPG, PNG, WEBP, ZIP)
                    FormatCategory.DOCUMENT -> if (source == PDF) listOf(PDF, ZIP) else listOf(ZIP)
                    else -> listOf(ZIP)
                }
            }
            return when (source) {
                PDF -> listOf(DOCX, PNG, JPG, WEBP, TXT, MD, HTML, PDF, ZIP)
                DOCX -> listOf(PDF, TXT, MD, HTML, ZIP)
                TXT -> listOf(PDF, DOCX, MD, HTML, ZIP)
                MD -> listOf(PDF, DOCX, HTML, TXT, ZIP)
                HTML -> listOf(PDF, DOCX, TXT, MD, ZIP)
                XLSX -> listOf(CSV, TSV, JSON, XML, HTML, PDF, ZIP)
                CSV -> listOf(XLSX, TSV, JSON, XML, HTML, PDF, ZIP)
                TSV -> listOf(XLSX, CSV, JSON, XML, HTML, PDF, ZIP)
                JSON -> listOf(CSV, XLSX, TSV, XML, HTML, PDF, TXT, ZIP)
                XML -> listOf(JSON, CSV, XLSX, TSV, HTML, PDF, TXT, ZIP)
                PNG -> listOf(JPG, WEBP, PDF, BMP, PNG, ZIP)
                JPG -> listOf(PNG, WEBP, PDF, BMP, JPG, ZIP)
                WEBP -> listOf(PNG, JPG, PDF, BMP, WEBP, ZIP)
                BMP -> listOf(PNG, JPG, WEBP, PDF, ZIP)
                ZIP -> listOf(ZIP)
            }
        }
    }
}
