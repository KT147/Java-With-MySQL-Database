package Challenge;

import com.mysql.cj.jdbc.MysqlDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PreparedStatement {

	private static String USE_SCHEMA = "USE storefront";

	private static int MYSQL_DB_NOT_FOUND = 1049;

	public static void main(String[] args) {
		var dataSource = new MysqlDataSource();
		dataSource.setServerName("localhost");
		dataSource.setPort(3306);
		dataSource.setUser(System.getenv("MYSQLUSER"));
		dataSource.setPassword(System.getenv("MYSQLPASS"));


		try (Connection conn = dataSource.getConnection()) {
			addOrdersFromFile(conn);
//			deleteOrder(conn, 4);
			DatabaseMetaData metaData = conn.getMetaData();
			System.out.println(metaData.getSQLStateType());

			if (!checkSchema(conn)) {
				System.out.println("storefront schema does not exist");
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

	private static int createOrder(Connection conn, String orderDate) throws SQLException {

		String sql = "INSERT INTO storefront.order (order_date) VALUES (?)";

		try (java.sql.PreparedStatement ps =
					 conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, orderDate);

			ps.executeUpdate();

			ResultSet rs = ps.getGeneratedKeys();

			if (!rs.next()) {
				throw new SQLException("Order ID not generated");
			}

			return rs.getInt(1);
		}
	}

	private static void insertItem(Connection conn, int orderId, String description, int quantity) throws SQLException {

		String sql = "INSERT INTO storefront.order_details (order_id, item_description, order_qty) VALUES (?, ?, ?)";

		try (java.sql.PreparedStatement ps =
					 conn.prepareStatement(sql)) {

			ps.setInt(1, orderId);
			ps.setString(2, description);
			ps.setInt(3, quantity);

			ps.executeUpdate();
		}

	}



	private static void addOrdersFromFile(Connection conn) throws SQLException {

		int currentOrderId = -1;


		try {
			conn.setAutoCommit(false);
			List<String> lines = Files.readAllLines(Path.of("Orders.csv"));

			for (String line : lines) {
				String [] parts = line.split(",");
				String recordType = parts[0];

				if (recordType.equals("order")) {
						String orderDate = parts[1];
						currentOrderId = createOrder(conn, orderDate);
				} else if (recordType.equals("item")) {
					String itemDescription = parts[2];
					int quantity = Integer.parseInt(parts[1]);
					insertItem(conn, currentOrderId, itemDescription, quantity);
				}
			}

			conn.commit();
		} catch (IOException e) {
			conn.rollback();
			throw new RuntimeException(e);
		}
		finally {

			conn.setAutoCommit(true);
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
