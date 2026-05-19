package com.giwon.babylog.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["com.giwon.babylog"])
@EnableScheduling
class BabyLogApiApplication

fun main(args: Array<String>) {
    runApplication<BabyLogApiApplication>(*args)
}
