var SERVER = process.env.NODE_ENV === "production" ? "147.100.179.128" : "localhost:8081";
var constants = {
  BASE: "http://" + SERVER + "/api/v1/",
  API_URL: "http://" + SERVER + "/api/v1/",
  WS_URL: "wss://" + SERVER + "/api/V1/",
  SWAGGER: "http://" + SERVER + "/swagger-ui.html",
};
export default constants;
