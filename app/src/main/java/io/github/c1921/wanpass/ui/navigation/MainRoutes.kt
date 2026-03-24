package io.github.c1921.wanpass.ui.navigation

import io.github.c1921.wanpass.domain.model.VaultItemType

object MainRoutes {
    const val Home = "home"
    const val CreateType = "create_type"
    const val NewItem = "new/{type}"
    const val EditItem = "edit/{itemId}"
    const val Detail = "detail/{itemId}"
    const val Settings = "settings"
    const val RecoveryCode = "recovery_code"

    fun newItem(type: VaultItemType): String = "new/${type.storageValue}"
    fun editItem(itemId: String): String = "edit/$itemId"
    fun detail(itemId: String): String = "detail/$itemId"
}
