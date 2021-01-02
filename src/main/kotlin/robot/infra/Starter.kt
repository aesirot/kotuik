package robot.infra

fun main() {
    Starter().start()
}

class Starter {

    fun start() {
        Scheduler().schedule()
        Pult().console()
    }

}