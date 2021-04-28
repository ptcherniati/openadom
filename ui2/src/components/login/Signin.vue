<template>
  <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
    <section>
      <ValidationProvider
        rules="required|email"
        name="email"
        v-slot="{ errors, valid }"
        vid="email"
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
            {{ $t("login.email") }}
            <span class="mandatory">
              {{ $t("validation.obligatoire") }}
            </span>
          </template>
          <b-input
            type="email"
            v-model="email"
            :placeholder="$t('login.email-placeholder')"
          >
          </b-input>
        </b-field>
      </ValidationProvider>

      <ValidationProvider
        rules="required"
        name="email"
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
    </section>

    <div class="buttons">
      <b-button
        type="is-primary"
        @click="handleSubmit(submit)"
        icon-right="plus"
      >
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
import { LoginService } from "@/services/LoginService";

@Component({
  components: { ValidationObserver, ValidationProvider },
})
export default class SignIn extends Vue {
  loginService = LoginService.INSTANCE;

  email = "";
  password = "";

  submit() {
    this.loginService.signIn(this.email, this.password);
  }
}
</script>
