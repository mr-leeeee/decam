package com.defectcamera.data.models

enum class DefectType(val label: String, val folder: String) {
    SCRATCH("스크래치", "Scratch"),
    PLATING("도금 불량", "PlatingDefect"),
    DENT("찍힘", "Dent"),
    OTHER("기타", "Other")
}
