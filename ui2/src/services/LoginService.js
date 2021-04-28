import { Fetcher } from "@/services/Fetcher.js";

export class LoginService extends Fetcher {
  static INSTANCE = new LoginService();

  constructor() {
    super();
  }

  async signIn(email, pwd) {
    return this.post("login", {
      login: email,
      password: pwd,
    });
  }
}
