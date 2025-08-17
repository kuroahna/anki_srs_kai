// Ideally, we would use the include_str!(...) macro but I can't figure out how
// to get crane (nix library) to include js files in the source when
// craneLib.buildDepsOnly is called

pub const HEADER: &str = r#"
const deckOptions = {
    "deck1": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor / Math.pow(currentInterval, 0.054297);
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
        },
    },
    "Global Settings": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            // Approximation of the default FSRS-6 parameters
            // [0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542]
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.077098162) + (0.144440985);
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.182458510) + (1.779479164);
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.183552566) + (3.407921198);
            },
        },
    },
};
"#;

pub const FOOTER: &str = r##"
const response = new Response(
    wasmBytes,
    {
        headers: {
            'Content-Type': 'application/wasm'
        }
    }
);

try {
    await wasm_bindgen(response);

    wasm_bindgen.calculateNextCardStates(deckOptions, states);
} catch (e) {
    function openCustomModal(message) {
        const modalContainer = document.createElement('div');
        modalContainer.id = 'custom-modal';

        const modalMessage = document.createElement('p');
        modalMessage.id = 'modal-message';
        modalMessage.innerText = message;
        modalMessage.style.fontFamily = 'Arial';

        const okButton = document.createElement('button');
        okButton.innerText = 'OK';
        okButton.onclick = closeCustomModal;

        modalContainer.appendChild(modalMessage);
        modalContainer.appendChild(okButton);

        modalContainer.style.display = 'none';
        modalContainer.style.position = 'fixed';
        modalContainer.style.top = '50%';
        modalContainer.style.left = '50%';
        modalContainer.style.transform = 'translate(-50%, -50%)';
        modalContainer.style.padding = '20px';
        modalContainer.style.backgroundColor = '#fff';
        modalContainer.style.border = '1px solid #ccc';
        modalContainer.style.boxShadow = '0 2px 10px rgba(0, 0, 0, 0.1)';
        modalContainer.style.zIndex = '1000';

        modalMessage.style.marginBottom = '15px';
        modalMessage.style.color = '#000';

        okButton.style.cursor = 'pointer';
        okButton.style.padding = '8px 16px';
        okButton.style.backgroundColor = '#007BFF';
        okButton.style.color = '#fff';
        okButton.style.border = 'none';
        okButton.style.borderRadius = '4px';

        document.body.appendChild(modalContainer);

        modalContainer.style.display = 'block';
    }

    function closeCustomModal() {
        const modalContainer = document.getElementById('custom-modal');
        document.body.removeChild(modalContainer);
    }

    openCustomModal(e.message);
}
"##;
