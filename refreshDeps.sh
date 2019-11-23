
cd ..
cd decidir-persistenceutils
sbt clean publish-local

cd ..
cd protocol-api
sbt clean publish-local

cd ..
cd coretx-api
sbt clean publish-local

cd ..
cd coretx
sbt update eclipse

