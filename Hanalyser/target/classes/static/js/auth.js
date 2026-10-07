// Shared auth utilities

function showAlert(type, message) {
    const errorEl = document.getElementById('errorAlert');
    const successEl = document.getElementById('successAlert');
    if (errorEl) { errorEl.style.display = 'none'; errorEl.textContent = ''; }
    if (successEl) { successEl.style.display = 'none'; successEl.textContent = ''; }

    if (type === 'error' && errorEl) {
        errorEl.textContent = '⚠ ' + message;
        errorEl.style.display = 'block';
    } else if (type === 'success' && successEl) {
        successEl.textContent = '✓ ' + message;
        successEl.style.display = 'block';
    }
    setTimeout(() => {
        if (errorEl) errorEl.style.display = 'none';
        if (successEl) successEl.style.display = 'none';
    }, 6000);
}

function togglePassword() {
    const input = document.getElementById('password');
    input.type = input.type === 'password' ? 'text' : 'password';
}

// OTP box navigation
document.addEventListener('DOMContentLoaded', () => {
    const otpBoxes = document.querySelectorAll('.otp-box');
    otpBoxes.forEach((box, i) => {
        box.addEventListener('input', (e) => {
            const val = e.target.value.replace(/[^0-9]/g, '');
            e.target.value = val;
            if (val && i < otpBoxes.length - 1) otpBoxes[i + 1].focus();
        });
        box.addEventListener('keydown', (e) => {
            if (e.key === 'Backspace' && !box.value && i > 0) otpBoxes[i - 1].focus();
        });
        box.addEventListener('paste', (e) => {
            e.preventDefault();
            const paste = (e.clipboardData || window.clipboardData).getData('text').replace(/\D/g, '');
            paste.split('').slice(0, 6).forEach((c, idx) => {
                if (otpBoxes[idx]) otpBoxes[idx].value = c;
            });
            otpBoxes[Math.min(paste.length, 5)].focus();
        });
    });

    // Check if already logged in
    if (window.location.pathname !== '/dashboard.html') {
        const token = localStorage.getItem('token');
        if (token && window.location.pathname !== '/') {
            // Don't redirect from auth pages automatically
        }
    }
});
