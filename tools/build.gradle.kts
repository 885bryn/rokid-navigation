plugins {
    application
    alias(libs.plugins.kotlin.jvm)
}
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":protocol")) }
application { mainClass.set("com.rokidnav.tools.DemoTripGeneratorKt") }
tasks.named<JavaExec>("run") { workingDir(rootProject.projectDir) }
