package com.winworld.coursestools.repository.ops;

import com.winworld.coursestools.entity.ops.OpsAlertCategorySetting;
import com.winworld.coursestools.enums.OpsAlertCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsAlertCategorySettingRepository extends JpaRepository<OpsAlertCategorySetting, OpsAlertCategory> {
}
