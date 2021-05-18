import app from "@/main";
import { User } from "@/model/User";
import { Fetcher, LOCAL_STORAGE_LOGGUED_USER } from "@/services/Fetcher.js";

export class LoginService extends Fetcher {
  static INSTANCE = new LoginService();
  loggedUser = new User();

  constructor() {
    super();
  }

  getLoggedUser() {
    if (!this.loggedUser || !this.loggedUser.id) {
      this.loggedUser = JSON.parse(
        localStorage.getItem(LOCAL_STORAGE_LOGGUED_USER)
      );
    }
    return this.loggedUser;
  }

  async signIn(login, pwd) {
    let response = await this.post("login", {
      login: login,
      password: pwd,
    });

    this.loggedUser = response;
    localStorage.setItem(
      LOCAL_STORAGE_LOGGUED_USER,
      JSON.stringify(this.loggedUser)
    );

    app.$router.push("/applications");
    return Promise.resolve(response);
  }

  async logout() {
    await this.delete("logout");
    this.notifyCrendentialsLost();
  }
}
