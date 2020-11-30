package com.acuscorp.uberclone.common

import com.acuscorp.uberclone.remote.IGoogleApi
import com.acuscorp.uberclone.remote.RetrofitClient

class Common {
    companion object {
        private const val baseUrl = "http://maps.googleapis.com"
        fun getGoogleAPI(): IGoogleApi{
            return RetrofitClient.getClient(baseUrl).create(IGoogleApi::class.java)
        }
    }
}