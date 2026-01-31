package com.originspecs.specextractor.model;

public record Vehicle(
        String manufacturer,
        String model,
        String modelNumber,
        String categoryClassNumber,
        String engineModel,
        String engineDisplacement,
        String transmissionModel,
        String vehicleWeight,
        String seatingCapacity,
        String fuelEfficiencyKmPerLitre,
        String co2EmissionsPer1Km,
        String fy15FuelStandardKmL,
        String fy20FuelStandardKmL,
        String efficiencyImprovementMeasures,
        String fuelImprovementMeasures,
        String driveFormat,
        String other,
        String gasCertLevel,
        String fy15StandardAchievement,
        String fy20StandardAchievement)
        implements DataRecord{ }
