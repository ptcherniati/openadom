export default {
template: `
  <div>
   <input type="text" name="login" v-model="login"></input>
   <input type="password" name="password" v-model="password"></input>
   <button v-on:click="submitLogin">Login</button>
 </div>
`,
  data() {
    return {
      login: 'poussin',
      password: 'xxxxxxxxj'
    }
  },
  methods: {
    submitLogin() {
        let formData  = new FormData();
        formData.append("login", this.login);
        formData.append("password", this.password);
        fetch("/api/v1/login", {method: "POST", body: formData})
          .then(response => {
            if(response.ok) {
              console.log("login ok")
            } else {
              console.log("login ko", response.status )
            }
          })
          .catch(error => console.error("login ko", error));
    }
  }
}
