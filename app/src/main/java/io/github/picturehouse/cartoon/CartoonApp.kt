package io.github.picturehouse.cartoon

import android.app.Application

class CartoonApp : Application() {
    lateinit var controller: CartoonController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = CartoonController(this)
    }
}
