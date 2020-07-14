# spark-ml-analysis

An Apache Spark application used in Machine Learning predictions of reactive stock system transaction events from Apache Kafka (event bus) 

Via Docker in `docker-compose.yml` the Apache Spark setup is virtualized and `TransactionRegressorModel` is being built

App uses `RandomForestRegressor` to make predictions of future price of transactions via past events processing

When errors level is too high app triggers re-training to better fit to the data with changed pattern
