export class User {
  creationDate;
  id;
  login;
  password;
  updateDate;

  constructor() {
    this.creationDate = new Date();
    this.id = "";
    this.login = "";
    this.password = "";
    this.updateDate = new Date();
  }
}
