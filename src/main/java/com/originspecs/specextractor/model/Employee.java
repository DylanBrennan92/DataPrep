package com.originspecs.specextractor.model;

public record Employee(
        String id,
        String name,
        String department,
        String title,
        String hireDate,
        String salary,
        String status

) { }
