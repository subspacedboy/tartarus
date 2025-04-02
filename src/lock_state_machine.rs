#[derive(Debug)]
pub enum State {
    Start,
    CertificateLoaded,
    CodeConfirmed,
    Reset,
}

pub struct LockStateMachine {
    state: State,
}

impl LockStateMachine {
    pub fn new() -> Self {
        Self { state: State::Start }
    }

    pub fn transition(&mut self, new_state: State) {
        println!("Transitioning from {:?} to {:?}", self.state, new_state);
        self.state = new_state;
    }

    pub fn current_state(&self) -> &State {
        &self.state
    }
}