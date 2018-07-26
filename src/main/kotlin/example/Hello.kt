package example

import com.github.freewind.lostlist.ArrayLists
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections.observableArrayList
import javafx.scene.control.TableView
import tornadofx.*
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Types

val connection = run {
    Class.forName("org.h2.Driver")
    DriverManager.getConnection("jdbc:h2:mem:mydb", "sa", "sa")!!
}

data class User(private val id: Int, private val name: String) {
    val idProperty = SimpleIntegerProperty(id)
    val nameProperty = SimpleStringProperty(name)
}

private val data = observableArrayList<User>()

class HelloWorld : View() {

    private val columnMetas = getColumnMetaOfUserTable()

    private val user = mutableMapOf<String, SimpleStringProperty>().apply {
        columnMetas.forEach { meta ->
            this[meta.columnName] = SimpleStringProperty()
        }
    }

    init {
        data.addAll(loadDbUsers())
    }

    private lateinit var tableView: TableView<User>

    override val root = hbox {
        tableview<User>(data) {
            tableView = this
            column("id", User::idProperty)
            column("name", User::nameProperty)
        }
        vbox {
            form {
                fieldset("New Row") {
                    columnMetas.forEach { meta ->
                        field(meta.columnName) {
                            textfield(user[meta.columnName]!!)
                        }
                    }
                }
            }
            button("Add to database").setOnAction {
                createUser(user.mapValues { it.value.value })
            }
        }
    }

    private fun createUser(map: Map<String, String>) {
        val fields = columnMetas.map { it.columnName }.joinToString(", ")

        val sql = """insert into users($fields) values (${ArrayLists.createFilled(columnMetas.size, "?").joinToString(",")})"""
        val stmt = connection.prepareStatement(sql)
        columnMetas.forEachIndexed { i, meta ->
            val index = i + 1
            when (meta.type) {
                Types.VARCHAR -> stmt.setString(index, map[meta.columnName])
                Types.INTEGER -> stmt.setInt(index, map[meta.columnName]!!.toInt())
                else -> println("Unhandled type: $meta")
            }
        }
        stmt.execute()

        data.clear()
        data.addAll(loadDbUsers())
    }

    private fun getColumnMetaOfUserTable(): List<ColumnMeta> {
        val rs = connection.createStatement().executeQuery("select * from users limit 0")
        return getColumnMeta(rs)
    }

    private fun loadDbUsers(): List<User> {
        val users = mutableListOf<User>()
        val rs = connection.createStatement().executeQuery("select * from users")
        while (rs.next()) {
            users.add(User(id = rs.getInt("id"), name = rs.getString("name")))
        }
        return users
    }
}

class HelloWorldStyle : Stylesheet() {
    init {
        root {
            prefWidth = 600.px
            prefHeight = 400.px
        }
    }
}

class HelloWorldApp : App(HelloWorld::class, HelloWorldStyle::class)

// type: `java.sql.Types`
data class ColumnMeta(val columnName: String, val displayName: String, val type: Int, val required: Boolean)

private fun getColumnMeta(rs: ResultSet): List<ColumnMeta> {
    val metaData = rs.metaData
    return (1..metaData.columnCount).map { index ->
        ColumnMeta(
                columnName = metaData.getColumnName(index),
                displayName = metaData.getColumnLabel(index),
                type = metaData.getColumnType(index),
                required = metaData.isNullable(index) == ResultSetMetaData.columnNoNulls
        )
    }.toList()
}

private fun prepareDbData() {
    connection.createStatement().use { stmt ->
        with(stmt) {
            executeUpdate("create table users(id int primary key, name varchar(255))")
            executeUpdate("insert into users values(1, 'Hello')")
            executeUpdate("insert into users values(2, 'World')")
        }
    }
}

fun main(args: Array<String>) {
    prepareDbData()
    launch<HelloWorldApp>()
}