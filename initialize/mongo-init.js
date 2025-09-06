db = db.getSiblingDB("cv-db");

db.createUser({
    user: "user",
    pwd: "123456",
    roles: [
        {role: "readWrite", db: "cv-db"}
    ]
});
