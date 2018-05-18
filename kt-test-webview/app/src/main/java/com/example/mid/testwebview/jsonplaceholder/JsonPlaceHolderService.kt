package com.example.mid.testwebview.jsonplaceholder

import io.reactivex.Observable
import retrofit2.http.GET
import java.util.*

interface JsonPlaceHolderService {
    @GET("posts")
    fun posts(): Observable<List<Post>>
}