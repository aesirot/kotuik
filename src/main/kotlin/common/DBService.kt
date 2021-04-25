package common

import com.google.gson.GsonBuilder
import model.Bond
import model.RobotState
import model.robot.PolzuchiiSellState
import org.hibernate.Transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import robot.PolzuchiiSellRobot
import robot.Robot
import robot.StakanLogger
import robot.orel.Orel
import robot.orel.OrelOFZ
import java.time.LocalDateTime

object DBService {
    private val log: Logger = LoggerFactory.getLogger(this.javaClass)

    fun getBond(secCode: String): Bond {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            try {
                val query = session.createQuery("from Bond where code = :code", Bond::class.java)
                query.setParameter("code", secCode)

                return query.singleResult!!
            } catch (e: Exception) {
                val msg = "Error loading $secCode"
                log.error(msg, e)
                throw Exception(msg, e)
            }
        }
    }

    fun loadAllRobots(): ArrayList<Robot> {
        return loadRobots("")
    }

    private val gson = GsonBuilder().setDateFormat("dd.MM.yyyy HH:mm:ss").create()

    fun loadRobots(where: String): ArrayList<Robot> {
        val result = ArrayList<Robot>()

        HibernateUtil.getSessionFactory().openSession().use { session ->
            try {
                var queryString = "from RobotState"
                if (where.isNotEmpty()) {
                    queryString += " where $where"
                }

                val query = session.createQuery(queryString, RobotState::class.java)

                val gson = gson

                for (robotState in query.list()) {
                    if (robotState.type == "PolzuchiiSellRobot") {
                        val state = gson.fromJson(robotState.state, PolzuchiiSellState::class.java)
                        val robot = PolzuchiiSellRobot(state)

                        robot.name = robotState.id
                        if (robotState.parentId != null) {
                            robot.setParent(robotState.parentId!!)
                        }

                        result.add(robot)
                    } else if (robotState.type == "Orel") {
                        val robot = Orel()

                        result.add(robot)
                    } else if (robotState.type == "OrelOFZ") {
                        val robot = OrelOFZ()

                        result.add(robot)
                    } else if (robotState.type == "StakanLogger") {
                        val robot = StakanLogger()

                        result.add(robot)
                    } else {
                        throw Exception("unsupported robot " + robotState.type)
                    }
                }
            } catch (e: Exception) {
                val msg = "Error loading robots"
                log.error(msg, e)
                throw Exception(msg, e)
            }
        }

        return result
    }

    fun saveNewRobot(robot: Robot) {
        HibernateUtil.getSessionFactory().currentSession.use { session ->
            var transaction: Transaction? = null
            try {
                transaction = session.beginTransaction()
                session.clear()

                val robotState = RobotState()
                robotState.id = robot.name()
                robotState.type = robot::class.java.simpleName
                if (robot.state() != null) {
                    robotState.state = gson.toJson(robot.state())
                }

                robotState.parentId = robot.getParent()

                session.save(robotState)

                transaction.commit()
            } catch (e: java.lang.Exception) {
                transaction?.rollback()
                log.error(e.message, e)
            }
        }

    }

    fun updateRobot(robot: Robot) {
        HibernateUtil.getSessionFactory().currentSession.use { session ->
            var transaction: Transaction? = null
            try {
                transaction = session.beginTransaction()
                session.clear()

                val robotState = session.get(RobotState::class.java, robot.name())
                if (robot.state() != null) {
                    robotState.state = gson.toJson(robot.state())
                }
                robotState.updated = LocalDateTime.now()
                session.save(robotState)

                transaction.commit()
            } catch (e: java.lang.Exception) {
                transaction?.rollback()
                log.error(e.message, e)
            }
        }

    }

    fun deleteRobot(robotId: String) {
        HibernateUtil.getSessionFactory().currentSession.use { session ->
            var transaction: Transaction? = null
            try {
                transaction = session.beginTransaction()
                session.clear()

                val robot = RobotState()
                robot.id = robotId

                session.delete(robot)

                transaction.commit()
            } catch (e: java.lang.Exception) {
                transaction?.rollback()
                e.printStackTrace()
            }
        }

    }

}