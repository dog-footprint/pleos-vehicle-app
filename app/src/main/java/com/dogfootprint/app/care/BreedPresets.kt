package com.dogfootprint.app.care

import com.dogfootprint.app.data.CoatType
import com.dogfootprint.app.data.CoatType.DOUBLE
import com.dogfootprint.app.data.CoatType.LONG_SINGLE
import com.dogfootprint.app.data.CoatType.SHORT
import com.dogfootprint.app.data.DogSize
import com.dogfootprint.app.data.DogSize.LARGE
import com.dogfootprint.app.data.DogSize.MEDIUM
import com.dogfootprint.app.data.DogSize.SMALL
import com.dogfootprint.app.data.DogSize.TOY

/**
 * Common breeds in Korea with the traits the care rules depend on. The user can override the
 * derived traits on the profile screen; this only provides defaults.
 */
data class BreedPreset(
    val key: String,
    val nameKo: String,
    val nameEn: String,
    val size: DogSize,
    val coat: CoatType,
    val brachycephalic: Boolean = false,
)

object BreedPresets {
    const val OTHER_KEY = "other"

    val all: List<BreedPreset> = listOf(
        BreedPreset("maltese", "말티즈", "Maltese", TOY, LONG_SINGLE),
        BreedPreset("poodle", "푸들", "Poodle", SMALL, LONG_SINGLE),
        BreedPreset("pomeranian", "포메라니안", "Pomeranian", TOY, DOUBLE),
        BreedPreset("chihuahua", "치와와", "Chihuahua", TOY, SHORT),
        BreedPreset("bichon", "비숑 프리제", "Bichon Frise", SMALL, LONG_SINGLE),
        BreedPreset("shih_tzu", "시츄", "Shih Tzu", SMALL, LONG_SINGLE, brachycephalic = true),
        BreedPreset("pug", "퍼그", "Pug", SMALL, SHORT, brachycephalic = true),
        BreedPreset("french_bulldog", "프렌치 불도그", "French Bulldog", SMALL, SHORT, brachycephalic = true),
        BreedPreset("boston_terrier", "보스턴 테리어", "Boston Terrier", SMALL, SHORT, brachycephalic = true),
        BreedPreset("dachshund", "닥스훈트", "Dachshund", SMALL, SHORT),
        BreedPreset("yorkshire", "요크셔 테리어", "Yorkshire Terrier", TOY, LONG_SINGLE),
        BreedPreset("spitz", "스피츠", "Japanese Spitz", SMALL, DOUBLE),
        BreedPreset("corgi", "웰시코기", "Welsh Corgi", MEDIUM, DOUBLE),
        BreedPreset("shiba", "시바견", "Shiba Inu", MEDIUM, DOUBLE),
        BreedPreset("jindo", "진돗개", "Jindo", MEDIUM, DOUBLE),
        BreedPreset("border_collie", "보더콜리", "Border Collie", MEDIUM, DOUBLE),
        BreedPreset("beagle", "비글", "Beagle", MEDIUM, SHORT),
        BreedPreset("cocker", "코카 스패니얼", "Cocker Spaniel", MEDIUM, LONG_SINGLE),
        BreedPreset("golden", "골든 리트리버", "Golden Retriever", LARGE, DOUBLE),
        BreedPreset("labrador", "래브라도 리트리버", "Labrador Retriever", LARGE, DOUBLE),
        BreedPreset("husky", "시베리안 허스키", "Siberian Husky", LARGE, DOUBLE),
        BreedPreset("samoyed", "사모예드", "Samoyed", LARGE, DOUBLE),
        BreedPreset("malamute", "알래스칸 말라뮤트", "Alaskan Malamute", LARGE, DOUBLE),
        BreedPreset("shepherd", "저먼 셰퍼드", "German Shepherd", LARGE, DOUBLE),
        BreedPreset("greyhound", "그레이하운드", "Greyhound", LARGE, SHORT),
        BreedPreset(OTHER_KEY, "믹스 / 기타", "Mixed / Other", MEDIUM, SHORT),
    )

    fun byKey(key: String): BreedPreset = all.firstOrNull { it.key == key } ?: all.last()
}
