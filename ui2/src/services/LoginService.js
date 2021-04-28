import { Fetcher } from "@/services/Fetcher.js";

export class LoginService extends Fetcher {
  static INSTANCE = new LoginService();

  constructor() {
    super();
  }

  async signIn(login, pwd) {
    return this.post("login", {
      login: login,
      password: pwd,
    });
  }
}
