package com.github.netherald.sqlWrapper

import com.github.netherald.sqlWrapper.models.Guild
import com.github.netherald.sqlWrapper.models.User
import org.json.simple.JSONArray
import org.json.simple.parser.JSONParser
import java.lang.IllegalStateException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*
import java.util.logging.Logger
import kotlin.collections.ArrayList

class SqlWrapper {

    var sqlConnection: Connection
    var logger: Logger

    constructor(username: String, password: String, logger: Logger, ip: String = "localhost", port: Int = 3306) {
        this.logger = logger
        logger.info("Loading driver...")
        try {
            Class.forName("com.mysql.cj.jdbc.Driver")
            logger.info("Connecting to SQL...")
            if(password == "") {
                sqlConnection = DriverManager.getConnection(ip, username, null)
            } else {
                sqlConnection = DriverManager.getConnection(ip, username, password)
            }
            logger.info("Connected to ${ip}:${port}")
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("Driver not found!")
        } catch (e: SQLException) {
            logger.info("Check the exception!")
            e.printStackTrace()
            logger.info("==============================================");
            throw UnknownError("Unknown Error!")
        }
    }

    fun closeConnection() {
        try {
            if (sqlConnection != null || !sqlConnection.isClosed) {
                sqlConnection.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    fun getGuild(id: Int) : Guild {
        if (sqlConnection.isClosed)
            throw IllegalStateException("SQL Connection is Closed!")
        val statement = sqlConnection.prepareStatement("SELECT * FROM netherald.guilds WHERE id=?")
        statement.setInt(1, id)

        val result = statement.executeQuery()

        while (result.next()) {
            val list = JSONParser().parse(result.getString("users")) as JSONArray
            val listParsed = ArrayList<User>()
            for (str in list) {
                listParsed.add(User(str as String, null, null))
            }
            return Guild(result.getString("name"), result.getInt("id"), listParsed, result.getString("description"))
        }
        throw IllegalAccessException("No guild found!")
    }

    fun getUser(uuid: UUID) : User {
        if (sqlConnection.isClosed)
            throw IllegalStateException("SQL Connection is Closed!")
        val statement = sqlConnection.prepareStatement("SELECT * FROM netherald.users WHERE uuid=?")
        statement.setString(1, uuid.toString())

        val result = statement.executeQuery()

        while (result.next()) {
            val list = JSONParser().parse(result.getString("friends")) as JSONArray
            val listParsed = ArrayList<User>()
            for (str in list) {
                listParsed.add(getUserWithoutFriends(UUID.fromString(str.toString())))
            }
            return User(uuid.toString(), if (result.getInt("guild") == 0) null else getGuild(result.getInt("guild")), listParsed)
        }
        throw IllegalAccessException("No user found!")
    }

    fun initUser(uuid: UUID) {
        createUser(uuid, 0, "[]")
    }

    private fun createUser(uuid: UUID, guild: Int, friendsJson: String) {
        if (sqlConnection.isClosed)
            throw IllegalStateException("SQL Connection is Closed!")
        val statement = sqlConnection.prepareStatement("INSERT INTO netherald.users VALUES (?, ?, ?)")
        statement.setString(1, uuid.toString())
        statement.setInt(2, guild)
        statement.setString(3, friendsJson)

        statement.executeUpdate()
    }

    fun initGuild(name: String, ownerUUID: UUID) {
        if (sqlConnection.isClosed)
            throw IllegalStateException("SQL Connection is Closed!")
        val statement = sqlConnection.prepareStatement("INSERT INTO netherald.guilds(name, description, users, owner) VALUES (?, ?, ?, ?)")
        statement.setString(1, name)
        statement.setString(2, "A netherald guild")
        statement.setString(3, "[\"$ownerUUID\"]")
        statement.setString(4, ownerUUID.toString())

        statement.executeUpdate()
    }

    private fun getUserWithoutFriends(uuid: UUID) : User {
        if (sqlConnection.isClosed)
            throw IllegalStateException("SQL Connection is Closed!")
        val statement = sqlConnection.prepareStatement("SELECT * FROM netherald.users WHERE uuid=?")
        statement.setString(1, uuid.toString())

        val result = statement.executeQuery()

        while (result.next()) {
            return User(uuid.toString(), getGuild(result.getInt("guild")), null)
        }
        throw IllegalAccessException("No user found!")
    }
}