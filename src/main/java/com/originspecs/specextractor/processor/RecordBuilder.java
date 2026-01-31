package com.originspecs.specextractor.processor;

import com.originspecs.specextractor.model.Employee;
import com.originspecs.specextractor.model.Vehicle;

import java.util.List;
import java.util.function.Function;

public class RecordBuilder {

    public static RowParser.Validator<Employee> employeeValidator = employee ->
            employee.id() != null && !employee.id().isBlank() &&
                    employee.name() != null && !employee.name().isBlank();

    public static RowParser.Validator<Vehicle> vehicleValidator = vehicle ->
            vehicle.engineModel() != null && !vehicle.engineModel().isBlank() &&
                    vehicle.modelNumber() != null && !vehicle.modelNumber().isBlank();

    // Now takes List<String> of cell values
    public static Function<List<String>, Employee> employeeBuilder = cellValues ->
            new Employee(
                    getValue(cellValues, 0),
                    getValue(cellValues, 1),
                    getValue(cellValues, 2),
                    getValue(cellValues, 3),
                    getValue(cellValues, 4),
                    getValue(cellValues, 5),
                    getValue(cellValues, 6)
            );

    public static Function<List<String>, Vehicle> vehicleBuilder = cellValues ->
            new Vehicle(
                    getValue(cellValues, 0),
                    getValue(cellValues, 1),
                    getValue(cellValues, 2),
                    getValue(cellValues, 3),
                    getValue(cellValues, 4),
                    getValue(cellValues, 5),
                    getValue(cellValues, 6),
                    getValue(cellValues, 7),
                    getValue(cellValues, 8),
                    getValue(cellValues, 9),
                    getValue(cellValues, 10),
                    getValue(cellValues, 11),
                    getValue(cellValues, 12),
                    getValue(cellValues, 13),
                    getValue(cellValues, 14),
                    getValue(cellValues, 15),
                    getValue(cellValues, 16),
                    getValue(cellValues, 17),
                    getValue(cellValues, 18),
                    getValue(cellValues, 19)
            );

    private static String getValue(List<String> values, int index) {
        return index < values.size() ? values.get(index) : "";
    }
}