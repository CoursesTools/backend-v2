INSERT INTO subscription_types (name, display_name)
VALUES
    ('COURSESTOOLSPRO', 'CoursesTools Pro'),
    ('MENTORSHIP', 'Mentorship');

INSERT INTO subscription_plans (subscription_type_id, name, display_name, price, duration_days, discount_multiplier)
VALUES
    (1, 'MONTH', 'CoursesTools Pro Month', 2999, 30, 1),
    (1, 'YEAR', 'CoursesTools Pro Year', 28999, 365, 0.5),
    (1, 'LIFETIME', 'CoursesTools Pro Lifetime', 48000, 9999, 0.33),
    (2, 'MONTH', 'Mentorship Month', 3999, 30, 1);