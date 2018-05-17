package com.example.mid.testwebview.php

import io.reactivex.Observable
import retrofit2.http.GET

interface PhpServerService {
    @GET("api.php")
    fun callApi():Observable<HmacData>
}