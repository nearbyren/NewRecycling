```

val url1 = "https:azshareappr.3322.cc/appfile/com.muzhiwan.gamehelper.installer.apk?time=1746001534&key=fdf2beb6e768d2c6ee07ee4fbd0166e5"
val filePath2 =  File(filesDir.path + "/userAvatar","ttt.apk")
DownloadManager.download(url1,filePath2)
                    .collectLatest { status  ->
                            when(status){
                                is DownloadStatus.Progress->{
                                    println("测试我来了 Progress ${status.value}")
                                }
                                is DownloadStatus.Error ->{
                                    println("测试我来了 Error ${status.throwable.message}")
                                }


                                is DownloadStatus.Done -> {
                                    println("测试我来了 Done ${status.file.path}")
                                }

                                DownloadStatus.None -> {
                                    println("测试我来了 None $status")

                                }
                            }
                    }


```


```

val url2 = "https:azshareappr.3322.cc/appfile/com.muzhiwan.gamehelper.installer.apk?time=1746001534&key=fdf2beb6e768d2c6ee07ee4fbd0166e5"
val filePath2 = "File(filesDir.path + "/userAvatar","ttt.apk").toString()"
                SingleDownloader(CorHttp.getInstance().getClient())
                    .onStart {
                        println("测试我来了 onStart ")
                    }
                    .onProgress{current, total, progress ->
                        println("测试我来了 onProgress $current $total $progress")

                    }.onSuccess { url, file ->
                        println("测试我来了 onSuccess $url ${file.path} $")

                    }
                    .onError { url, cause ->
                        println("测试我来了 onError $url ${cause.message} ")

                    }
                    .onCompletion { url, filePath ->
                        println("测试我来了 onCompletion $url ${filePath} ")

                    }
                    .excute(url2,filePath2 )
```