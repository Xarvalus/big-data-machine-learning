# Makefile

run:
	sbt "runAll"

simulate:
	go run ./interpolator/main.go

train:
	cd spark-ml-analysis/ && \
	sbt package && \
	docker-compose up

bash-spark:
	docker exec -it spark-ml-analysis_spark_1 bash

# Clean Kafka / Cassandra data
clean:
	rm -rf target/embedded-cassandra/data/*
	rm -rf target/lagom-dynamic-projects/lagom-internal-meta-project-kafka/target/kafka_data/*
	rm -rf target/lagom-dynamic-projects/lagom-internal-meta-project-kafka/target/zookeeper_data/*

remove-targets:
	find . -path '*/target*' -delete
