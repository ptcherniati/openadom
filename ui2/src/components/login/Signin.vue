<template>
  <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
    <section>
      <ValidationProvider rules="required" name="login" v-slot="{ errors, valid }" vid="login">
        <b-field
          class="input-field"
          :type="{
            'is-danger': errors && errors.length > 0,
            'is-success': valid,
          }"
          :message="errors[0]"
        >
          <template slot="label">
            {{ $t("login.login") }}
            <span class="mandatory">
              {{ $t("validation.obligatoire") }}
            </span>
          </template>
          <b-input v-model="login" :placeholder="$t('login.login-placeholder')"> </b-input>
        </b-field>
      </ValidationProvider>

      <ValidationProvider
        rules="required"
        name="password"
        v-slot="{ errors, valid }"
        vid="password"
      >
        <b-field
          class="input-field"
          :type="{
            'is-danger': errors && errors.length > 0,
            'is-success': valid,
          }"
          :message="errors[0]"
        >
          <template slot="label">
            {{ $t("login.pwd") }}
            <span class="mandatory">
              {{ $t("validation.obligatoire") }}
            </span>
          </template>
          <b-input
            type="password"
            v-model="password"
            :placeholder="$t('login.pwd-placeholder')"
            :password-reveal="true"
            @keyup.native.enter="handleSubmit(signIn)"
          >
          </b-input>
        </b-field>
      </ValidationProvider>
    </section>

    <div class="buttons">
      <b-button type="is-primary" @click="handleSubmit(signIn)" icon-right="plus">
        {{ $t("login.signin") }}
      </b-button>
      <router-link :to="{ path: '/' }">
        {{ $t("login.pwd-forgotten") }}
      </router-link>
    </div>
  </ValidationObserver>
</template>

<script>
import { Component, Vue } from "vue-property-decorator";
import { ValidationObserver, ValidationProvider } from "vee-validate";
import { LoginService } from "@/services/rest/LoginService";
import { AlertService } from "@/services/AlertService";
import { HttpStatusCodes } from "@/utils/HttpUtils";

@Component({
  components: { ValidationObserver, ValidationProvider },
})
export default class SignIn extends Vue {
  loginService = LoginService.INSTANCE;
  alertService = AlertService.INSTANCE;

  login = "";
  password = "";

  async signIn() {
    try {
      await this.loginService.signIn(this.login, this.password);
    } catch (error) {
      if (error.httpResponseCode === HttpStatusCodes.FORBIDDEN) {
        this.alertService.toastError(this.$t("alert.user-unknown"), error);
      } else {
        this.alertService.toastServerError(error);
      }
    }
  }
}
</script>
