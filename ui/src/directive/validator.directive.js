import Vue from 'vue';
export default Vue.directive(
    'validator', {
        update(element, binding) {
            const value = element.value;
            if (binding.modifiers.login) {
                //verification login
                if (value.length < 4 || value.length > 26) {
                    element.style.outlineColor = 'red'
                } else {
                    element.style.outlineColor = 'green'
                }
            } else if (binding.modifiers.password) {
                //verification password
            }
        }
    }
);