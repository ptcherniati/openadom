import app from "@/main";
import { User } from "@/model/User";
import { Fetcher, LOCAL_STORAGE_AUTHENTICATED_USER } from "@/services/Fetcher.js";

export class LoginService extends Fetcher {
  static INSTANCE = new LoginService();
  authenticatedUser = new User();

  constructor() {
    super();
  }

  getAuthenticatedUser() {
    if (!this.authenticatedUser || !this.authenticatedUser.id) {
      this.authenticatedUser = JSON.parse(localStorage.getItem(LOCAL_STORAGE_AUTHENTICATED_USER));
    }
    return this.authenticatedUser;
  }

  async signIn(login, pwd) {
    let response = await this.post("login", {
      login: login,
      password: pwd,
    });

    this.authenticatedUser = response;
    localStorage.setItem(LOCAL_STORAGE_AUTHENTICATED_USER, JSON.stringify(this.authenticatedUser));

    app.$router.push("/applications");
    return Promise.resolve(response);
  }

  async register(login, pwd) {
    return this.post("users", {
      login: login,
      password: pwd,
    });
  }

  async logout() {
    await this.delete("logout");
    this.notifyCrendentialsLost();
  }
}
