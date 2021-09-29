package mate.jdbc.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mate.jdbc.dao.CarDao;
import mate.jdbc.exception.DataProcessingException;
import mate.jdbc.lib.Dao;
import mate.jdbc.model.Car;
import mate.jdbc.model.Driver;
import mate.jdbc.model.Manufacturer;
import mate.jdbc.util.ConnectionUtil;

@Dao
public class CarDaoImpl implements CarDao {
    private static final String CANT_GET_ALL_CARS_MESSAGE = "Can't get all cars from DB.";
    private static final String CANT_CREATE_CAR_MESSAGE = "Can't insert car %s to DB.";
    private static final String CANT_UPDATE_CAR_MESSAGE = "Can't update car %s in DB.";
    private static final String CANT_DELETE_CAR_MESSAGE = "Can't delete car with id = %s from DB.";
    private static final String CANT_GET_CAR_MESSAGE = "Can't get car with id = %s from DB.";
    private static final String CANT_INSERT_DRIVERS_TO_CAR_MESSAGE = "Can't insert drivers to car ";
    private static final String CANT_GET_DRIVERS_TO_CAR_MESSAGE
            = "Can't get driver for car by car id = ";
    private static final String CANT_DELETE_DRIVERS_FROM_CAR_MESSAGE
            = "Can't delete drivers from car ";
    private static final String CANT_GET_ALL_CARS_OF_DRIVER_MESSAGE
            = "Can't get all cars with driverId = ";
    private static final String NAME = "name";
    private static final String LICENSE_NUMBER = "license_number";
    private static final String ID = "id";
    private static final String CAR_ID = "car_id";
    private static final String CAR_NAME = "car_name";
    private static final String MANUFACTURER_ID = "manufacturer_id";
    private static final String MANUFACTURER_NAME = "manufacturer_name";
    private static final String MANUFACTURER_COUNTRY = "manufacturer_country";

    @Override
    public Car create(Car car) {
        String createCarRequest
                = "INSERT INTO cars(name, manufacturer_id) VALUES(?, ?);";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement createCarStatement =
                         connection.prepareStatement(createCarRequest,
                             Statement.RETURN_GENERATED_KEYS)) {
            createCarStatement.setString(1, car.getName());
            createCarStatement.setLong(2, car.getManufacturer().getId());
            createCarStatement.executeUpdate();
            ResultSet generatedKeys = createCarStatement.getGeneratedKeys();
            if (generatedKeys.next()) {
                Long id = generatedKeys.getObject(1, Long.class);
                car.setId(id);
            }
        } catch (SQLException e) {
            throw new RuntimeException(String.format(CANT_CREATE_CAR_MESSAGE, car), e);
        }
        insertDriversToCar(car);
        return car;
    }

    @Override
    public Optional<Car> get(Long id) {
        String getCarRequest = "SELECT"
                + " cars.id AS car_id,"
                + " cars.name AS car_name,"
                + " manufacturers.id AS manufacturer_id,"
                + " manufacturers.country AS manufacturer_country,"
                + " manufacturers.name AS manufacturer_name,"
                + " manufacturers.country"
                + " FROM cars JOIN manufacturers ON cars.manufacturer_id = manufacturers.id "
                + " WHERE cars.id = ? AND cars.is_deleted = false;";
        Car car = null;
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement getCarStatement
                         = connection.prepareStatement(getCarRequest)) {
            getCarStatement.setLong(1, id);
            ResultSet resultSet = getCarStatement.executeQuery();
            if (resultSet.next()) {
                car = parseCarFromResultSet(resultSet);
            }
        } catch (SQLException e) {
            throw new RuntimeException(String.format(CANT_GET_CAR_MESSAGE, id), e);
        }
        if (car != null) {
            car.setDriverList(getDriversForCar(car.getId()));
        }
        return Optional.ofNullable(car);
    }

    @Override
    public List<Car> getAll() {
        String getAllCars = "SELECT"
                + " cars.id AS 'car_id',"
                + " cars.name AS 'car_name',"
                + " manufacturers.id AS 'manufacturer_id',"
                + " manufacturers.name AS 'manufacturer_name',"
                + " manufacturers.country AS 'manufacturer_country'"
                + " FROM cars JOIN manufacturers ON cars.manufacturer_id = manufacturers.id"
                + " WHERE cars.is_deleted = false;";
        List<Car> cars = new ArrayList<>();
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement getAllCarsStatement = connection.prepareStatement(getAllCars)) {
            ResultSet resultSet = getAllCarsStatement.executeQuery();
            while (resultSet.next()) {
                cars.add(parseCarFromResultSet(resultSet));
            }
        } catch (SQLException e) {
            throw new RuntimeException(CANT_GET_ALL_CARS_MESSAGE, e);
        }
        for (Car car : cars) {
            car.setDriverList(getDriversForCar(car.getId()));
        }
        return cars;
    }

    @Override
    public Car update(Car car) {
        String updateCarRequest
                = "UPDATE cars"
                + " SET name = ?, manufacturer_id = ? "
                + " WHERE id = ? AND is_deleted = false; ";
        try (Connection connection
                     = ConnectionUtil.getConnection();
                 PreparedStatement updateCarStatement
                         = connection.prepareStatement(updateCarRequest)) {
            updateCarStatement.setString(1, car.getName());
            updateCarStatement.setObject(2, car.getManufacturer().getId());
            updateCarStatement.setObject(3, car.getId());
            updateCarStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(String.format(CANT_UPDATE_CAR_MESSAGE, car), e);
        }
        deleteDriversFromCar(car);
        insertDriversToCar(car);
        return car;
    }

    @Override
    public boolean delete(Long id) {
        String deleteCarRequest
                = "UPDATE cars SET is_deleted = TRUE WHERE id = ?";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement deleteCarStatement
                         = connection.prepareStatement(deleteCarRequest)) {
            deleteCarStatement.setLong(1, id);
            return deleteCarStatement.executeUpdate() != 0;
        } catch (SQLException e) {
            throw new RuntimeException(String.format(CANT_DELETE_CAR_MESSAGE, id), e);
        }
    }

    @Override
    public List<Car> getAllByDriver(Long driverId) {
        String getAllCarsByDriverRequest
                = "SELECT"
                + " cars.id AS 'car_id',"
                + " cars.name AS 'car_name',"
                + " manufacturers.id AS 'manufacturer_id',"
                + " manufacturers.name AS 'manufacturer_name',"
                + " manufacturers.country AS 'manufacturer_country'"
                + " FROM cars"
                + " JOIN manufacturers ON cars.manufacturer_id = manufacturers.id"
                + " JOIN cars_drivers ON cars.id = cars_drivers.car_id "
                + " WHERE cars_drivers.driver_id = ? AND cars.is_deleted = false;";
        List<Car> cars = new ArrayList<>();
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement getAllCarsByDriverStatement
                        = connection.prepareStatement(getAllCarsByDriverRequest)) {
            getAllCarsByDriverStatement.setLong(1, driverId);
            ResultSet resultSet = getAllCarsByDriverStatement.executeQuery();
            while (resultSet.next()) {
                cars.add(parseCarFromResultSet(resultSet));
            }
        } catch (SQLException e) {
            throw new DataProcessingException(CANT_GET_ALL_CARS_OF_DRIVER_MESSAGE + driverId, e);
        }
        for (Car car : cars) {
            car.setDriverList(getDriversForCar(car.getId()));
        }
        return cars;
    }

    private void insertDriversToCar(Car car) {
        String addDriversToCarRequest
                = "INSERT INTO cars_drivers (car_id, driver_id) VALUES(?, ?) ;";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement addDriversToCarStatement
                         = connection.prepareStatement(addDriversToCarRequest)) {
            addDriversToCarStatement.setLong(1, car.getId());
            for (Driver driver : car.getDriverList()) {
                addDriversToCarStatement.setLong(2, driver.getId());
                addDriversToCarStatement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(CANT_INSERT_DRIVERS_TO_CAR_MESSAGE + car, e);
        }
    }

    private void deleteDriversFromCar(Car car) {
        String deleteDriversFromCarRequest = "DELETE FROM cars_drivers WHERE car_id = ? ;";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement deleteDriversFromCarStatement
                         = connection.prepareStatement(deleteDriversFromCarRequest)) {
            deleteDriversFromCarStatement.setLong(1, car.getId());
            deleteDriversFromCarStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(CANT_DELETE_DRIVERS_FROM_CAR_MESSAGE + car, e);
        }
    }

    private List<Driver> getDriversForCar(Long carId) {
        String getAllDriversForCarRequest
                = "SELECT drivers.id,"
                + " drivers.name,"
                + " drivers.license_number"
                + " FROM cars_drivers"
                + " JOIN drivers ON drivers.id = cars_drivers.driver_id"
                + " WHERE cars_drivers.car_id = ?;";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement getAllDriversStatement
                        = connection.prepareStatement(getAllDriversForCarRequest)) {
            getAllDriversStatement.setLong(1, carId);
            ResultSet resultSet = getAllDriversStatement.executeQuery();
            List<Driver> drivers = new ArrayList<>();
            while (resultSet.next()) {
                drivers.add(parseDriverForCarFromResultSet(resultSet));
            }
            return drivers;
        } catch (SQLException e) {
            throw new RuntimeException(CANT_GET_DRIVERS_TO_CAR_MESSAGE + carId, e);
        }
    }

    private Car parseCarFromResultSet(ResultSet resultSet) throws SQLException {
        Car car = new Car();
        car.setId(resultSet.getObject(CAR_ID,Long.class));
        car.setName(resultSet.getString(CAR_NAME));
        car.setManufacturer(parseManufacturerFromResultSet(resultSet));
        return car;
    }

    private Driver parseDriverForCarFromResultSet(ResultSet resultSet) throws SQLException {
        Driver driver = new Driver();
        driver.setId(resultSet.getObject(ID, Long.class));
        driver.setName(resultSet.getString(NAME));
        driver.setLicenseNumber(resultSet.getString(LICENSE_NUMBER));
        return driver;
    }

    private Manufacturer parseManufacturerFromResultSet(ResultSet resultSet) throws SQLException {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(resultSet.getObject(MANUFACTURER_ID, Long.class));
        manufacturer.setName(resultSet.getString(MANUFACTURER_NAME));
        manufacturer.setCountry(resultSet.getString(MANUFACTURER_COUNTRY));
        return manufacturer;
    }
}