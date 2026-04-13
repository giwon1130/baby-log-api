package com.giwon.babylog.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.giwon.babylog"])
class BabyLogApiApplication

fun main(args: Array<String>) {
    runApplication<BabyLogApiApplication>(*args)
}
