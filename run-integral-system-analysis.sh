#!bin/sh/
export FIREBASE_CREDENTIALS_FILE='/home/dale/lottery-util-firebase-credentials.json'
export FIREBASE_URL='https://lottery-util-dd398-default-rtdb.europe-west1.firebasedatabase.app'
export ASYNC='false'
export AUTOSAVE_EVERY='3000000'
export LOGGER_SHOW_THREAD_INFO='true'
export TASKS_MAX_PARALLEL='8'
mvn -B compile exec:java -P integral-system-analysis --file pom.xml