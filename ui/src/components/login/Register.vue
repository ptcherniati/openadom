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
          >
          </b-input>
        </b-field>
      </ValidationProvider>

      <ValidationProvider
        rules="required|confirmed:password"
        name="confirm_password"
        v-slot="{ errors, valid }"
        vid="confirm_password"
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
            {{ $t("login.confirm-pwd") }}
            <span class="mandatory">
              {{ $t("validation.obligatoire") }}
            </span>
          </template>
          <b-input
            type="password"
            v-model="confirmedPwd"
            :placeholder="$t('login.pwd-placeholder')"
            :password-reveal="true"
            @keyup.native.enter="handleSubmit(register)"
          >
          </b-input>
        </b-field>
      </ValidationProvider>
    </section>

    <div class="buttons">
      <b-button
        type="is-primary"
        @click="handleSubmit(register)"
        icon-left="user-plus"
        :aria-label="$t('login.aria-btn-signup')"
      >
        {{ $t("login.register") }}
      </b-button>
    </div>
  </ValidationObserver>
</template>

<script>
import { Component, Vue } from "vue-property-decorator";
import { ValidationObserver, ValidationProvider } from "vee-validate";
import { LoginService } from "@/services/rest/LoginService";
import { AlertService } from "@/services/AlertService";

@Component({
  components: { ValidationObserver, ValidationProvider },
})
export default class Register extends Vue {
  loginService = LoginService.INSTANCE;
  alertService = AlertService.INSTANCE;

  login = "";
  password = "";
  confirmedPwd = "";

  async register() {
    try {
      await this.loginService.register(this.login, this.password);
      this.alertService.toastSuccess(this.$t("alert.registered-user"));
      this.resetVariables();
      this.$emit("userRegistered");
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }

  resetVariables() {
    this.login = "";
    this.password = "";
    this.confirmedPwd = "";
  }
}
</script>
