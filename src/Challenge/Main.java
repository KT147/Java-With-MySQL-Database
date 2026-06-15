package Challenge;

import com.mysql.cj.jdbc.MysqlDataSource;

import javax.swing.plaf.nimbus.State;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {

	private static String USE_SCHEMA = "USE storefront";

	private static int MYSQL_DB_NOT_FOUND = 1049;

	public static void main(String[] args) {
		var dataSource = new MysqlDataSource();
		dataSource.setServerName("localhost");
		dataSource.setPort(3306);
		dataSource.setUser(System.getenv("MYSQLUSER"));
		dataSource.setPassword(System.getenv("MYSQLPASS"));

		try (Connection conn = dataSource.getConnection()) {

			DatabaseMetaData metaData = conn.getMetaData();
			System.out.println(metaData.getSQLStateType());
//			insertOrderDetails(conn, "Socks, Shoes, Shirt");
			deleteOrder(conn, 1);

			if (!checkSchema(conn)) {
				System.out.println("storefront schema does not exist");
//				setUpSchema(conn);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

	}

	private static boolean checkSchema(Connection conn) throws SQLException {

		try (Statement statement = conn.createStatement()) {
			statement.execute(USE_SCHEMA);
		} catch (SQLException e) {
			e.printStackTrace();
			System.err.println("SQLState: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());

			if (conn.getMetaData().getDatabaseProductName().equals("MySQL") && e.getErrorCode() == MYSQL_DB_NOT_FOUND) {
				return false;
			} else throw e;
		}
		return true;
	}

	private static void setUpSchema(Connection conn) throws SQLException {

		String createSchema = "CREATE SCHEMA storefront";

		String createOrder = """
				CREATE TABLE storefront.order(
				order_id INT NOT NULL AUTO_INCREMENT,
				order_date DATETIME NOT NULL,
				PRIMARY KEY (order_id)
				)""";

		String createOrderDetails = """
				CREATE TABLE storefront.order_details(
				order_detail_id INT NOT NULL AUTO_INCREMENT,
				item_description text,
				order_id INT DEFAULT NULL,
				PRIMARY KEY (order_detail_id),
				KEY FK_ORDERID (order_id),
				CONSTRAINT FK_ORDERID
				    FOREIGN KEY (order_id)
				    REFERENCES storefront.order(order_id)
				    ON DELETE CASCADE
				)""";

		try (Statement statement = conn.createStatement()) {
			System.out.println("Creating storefront Database");
			statement.execute(createSchema);
			if (checkSchema(conn)) {
				statement.execute(createOrder);
				System.out.println("Successfully Created Order");
				statement.execute(createOrderDetails);
				System.out.println("Successfully Created Order Details");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static void insertOrderDetails(Connection conn, String orderDetails)
			throws SQLException {

		LocalDateTime date = LocalDateTime.now();
		DateTimeFormatter formatter =
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

		String formattedDate = date.format(formatter);

		try (Statement statement = conn.createStatement()) {
			conn.setAutoCommit(false);

			try {
				String orderInsert = """
						INSERT INTO storefront.order (order_date)
						VALUES ('%s')
						""".formatted(formattedDate);
				statement.executeUpdate(orderInsert,
						Statement.RETURN_GENERATED_KEYS);
				ResultSet rs = statement.getGeneratedKeys();

				if (!rs.next()) {
					throw new SQLException("Order ID not generated");
				}

				int orderId = rs.getInt(1);
				System.out.println("Created order: " + orderId);
				String[] details = orderDetails.split(",");

				for (String detail : details) {
					detail = detail.trim();
					String orderDetailsInsert = """
							INSERT INTO storefront.order_details
							(order_id, item_description)
							VALUES (%d, '%s')
							""".formatted(orderId, detail);
					System.out.println(orderDetailsInsert);
					statement.executeUpdate(orderDetailsInsert);
				}

				conn.commit();

			} catch (SQLException e) {
				conn.rollback();
				throw e;
			} finally {
				conn.setAutoCommit(true);
			}
		}
	}

	private static void deleteOrder(Connection conn, int orderNo) throws SQLException {
		String deleteQuery = "DELETE FROM storefront.order WHERE order_id=%d"
				.formatted(orderNo);

		try (Statement statement = conn.createStatement()) {
			int deletedRecords = statement.executeUpdate(deleteQuery);
			System.out.println(deletedRecords);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
}
